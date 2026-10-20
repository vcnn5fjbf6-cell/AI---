import assert from "node:assert/strict";
import { once } from "node:events";
import test from "node:test";

import { createReleaseAgentServer } from "../src/server.js";

const username = "release-admin";
const password = "p".repeat(32);
const authorization = `Basic ${Buffer.from(`${username}:${password}`).toString("base64")}`;

async function withServer(service, action) {
  const server = createReleaseAgentServer({
    service,
    adminUsername: username,
    adminPassword: password,
    logger: { error() {} },
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  const baseUrl = `http://127.0.0.1:${server.address().port}`;
  try {
    await action(baseUrl);
  } finally {
    server.close();
    await once(server, "close");
  }
}

test("health is public while the UI and APIs require exact Basic authentication", async () => {
  const service = {
    getContext: () => ({ repository: "owner/repo" }),
    getBatches: () => [],
  };
  await withServer(service, async (baseUrl) => {
    const health = await fetch(`${baseUrl}/health`);
    assert.equal(health.status, 200);
    assert.deepEqual(await health.json(), { status: "ok" });

    for (const path of ["/", "/assets/app.js", "/api/context"]) {
      const unauthorized = await fetch(`${baseUrl}${path}`);
      assert.equal(unauthorized.status, 401);
      assert.match(unauthorized.headers.get("www-authenticate"), /^Basic /);
    }

    const wrong = await fetch(`${baseUrl}/`, {
      headers: { Authorization: `Basic ${Buffer.from(`${username}:wrong`).toString("base64")}` },
    });
    assert.equal(wrong.status, 401);

    const authorized = await fetch(`${baseUrl}/api/context`, {
      headers: { Authorization: authorization },
    });
    assert.equal(authorized.status, 200);
    assert.deepEqual(await authorized.json(), { repository: "owner/repo" });
    assert.equal(authorized.headers.has("access-control-allow-origin"), false);
  });
});

test("serves a self-contained Chinese UI with restrictive browser headers", async () => {
  const service = {
    getContext: () => ({ configured: false, candidates: [] }),
    getBatches: () => [],
  };
  await withServer(service, async (baseUrl) => {
    const response = await fetch(`${baseUrl}/`, { headers: { Authorization: authorization } });
    assert.equal(response.status, 200);
    assert.match(response.headers.get("content-security-policy"), /default-src 'self'/);
    assert.match(response.headers.get("content-security-policy"), /frame-ancestors 'none'/);
    assert.equal(response.headers.get("x-frame-options"), "DENY");
    const html = await response.text();
    assert.match(html, /上线管理/);
    assert.match(html, /创建批次并开始组合测试/);
    assert.match(html, /id="selected-count"/);
    assert.match(html, /id="clear-selection-button"/);
    assert.match(html, /\/assets\/app\.css/);
    assert.match(html, /\/assets\/app\.js/);
    assert.doesNotMatch(html, /https?:\/\//);

    const script = await fetch(`${baseUrl}/assets/app.js`, { headers: { Authorization: authorization } });
    assert.equal(script.status, 200);
    const scriptText = await script.text();
    assert.match(scriptText, /createBatch/);
    assert.match(scriptText, /clearSelection/);
  });
});

test("can be embedded only when explicit frame ancestors are configured", async () => {
  const service = {
    getContext: () => ({ configured: false, candidates: [] }),
    getBatches: () => [],
  };
  const server = createReleaseAgentServer({
    service,
    adminUsername: username,
    adminPassword: password,
    frameAncestors: "https://procurement-test-151.tailb89388.ts.net http://100.100.226.27:18080",
    logger: { error() {} },
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  try {
    const baseUrl = `http://127.0.0.1:${server.address().port}`;
    const response = await fetch(`${baseUrl}/`, { headers: { Authorization: authorization } });
    assert.equal(response.status, 200);
    assert.match(
      response.headers.get("content-security-policy"),
      /frame-ancestors https:\/\/procurement-test-151\.tailb89388\.ts\.net http:\/\/100\.100\.226\.27:18080/,
    );
    assert.equal(response.headers.has("x-frame-options"), false);
  } finally {
    server.close();
    await once(server, "close");
  }
});

test("server derives the audit actor from the authenticated administrator", async () => {
  let received;
  const service = {
    getContext: () => ({}),
    getBatches: () => [],
    createBatch(input) {
      received = input;
      return { batch: { id: "batch" }, replayed: false };
    },
  };
  await withServer(service, async (baseUrl) => {
    const response = await fetch(`${baseUrl}/api/batches`, {
      method: "POST",
      headers: {
        Authorization: authorization,
        "Content-Type": "application/json",
        "User-Agent": "release-ui-test",
      },
      body: JSON.stringify({
        prNumbers: [7],
        idempotencyKey: "create:123456",
        requestedBy: { username: "spoofed" },
      }),
    });
    assert.equal(response.status, 201);
  });
  assert.equal(received.requestedBy.username, username);
  assert.equal(received.requestedBy.realName, username);
  assert.equal(received.requestedBy.userAgent, "release-ui-test");
  assert.notEqual(received.requestedBy.clientIp, "");
});
