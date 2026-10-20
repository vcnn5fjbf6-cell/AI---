import assert from "node:assert/strict";
import { generateKeyPairSync, verify } from "node:crypto";
import test from "node:test";

import { GitHubAppTokenProvider } from "../src/github-app-auth.js";

test("GitHub App provider signs a JWT and reuses a valid installation token", async () => {
  const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const now = Date.parse("2026-07-15T00:00:00.000Z");
  let requests = 0;
  const provider = new GitHubAppTokenProvider({
    appId: "12345",
    installationId: "67890",
    privateKey: privateKey.export({ type: "pkcs8", format: "pem" }),
    now: () => now,
    fetchImpl: async (url, options) => {
      requests += 1;
      assert.equal(url, "https://api.github.com/app/installations/67890/access_tokens");
      const jwt = options.headers.Authorization.replace(/^Bearer /, "");
      const [header, payload, signature] = jwt.split(".");
      assert.equal(JSON.parse(Buffer.from(header, "base64url")).alg, "RS256");
      assert.equal(JSON.parse(Buffer.from(payload, "base64url")).iss, "12345");
      assert.equal(
        verify("RSA-SHA256", Buffer.from(`${header}.${payload}`), publicKey, Buffer.from(signature, "base64url")),
        true,
      );
      return new Response(JSON.stringify({
        token: "github-installation-token-value",
        expires_at: "2026-07-15T01:00:00.000Z",
      }), { status: 201, headers: { "Content-Type": "application/json" } });
    },
  });

  assert.equal(await provider.getToken(), "github-installation-token-value");
  assert.equal(await provider.getToken(), "github-installation-token-value");
  assert.equal(requests, 1);
});
