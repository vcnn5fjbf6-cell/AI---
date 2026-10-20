import assert from "node:assert/strict";
import test from "node:test";

import { loadConfig } from "../src/config.js";

const baseEnvironment = {
  RELEASE_GITHUB_OWNER: "example-owner",
  RELEASE_GITHUB_REPO: "example-repo",
  RELEASE_GITHUB_MAIN_BRANCH: "main",
  RELEASE_GITHUB_TEST_WORKFLOW: "deploy-test.yml",
  RELEASE_GITHUB_PRODUCTION_WORKFLOW: "deploy.yml",
  RELEASE_ADMIN_USERNAME: "release-admin",
  RELEASE_ADMIN_PASSWORD: "p".repeat(32),
};

const privateKeyBase64 = Buffer.from([
  "-----BEGIN PRIVATE KEY-----",
  "ZmFrZS1wcml2YXRlLWtleQ==",
  "-----END PRIVATE KEY-----",
  "",
].join("\n")).toString("base64");

test("missing GitHub App is a non-fatal unconfigured state", () => {
  const config = loadConfig(baseEnvironment);
  assert.equal(config.configured, false);
  assert.equal(config.githubAppId, null);
  assert.equal(config.configurationReason, "未配置仓库专属 GitHub App");
  assert.equal(config.port, 8080);
  assert.equal(config.stateFile, "/data/state.json");
  assert.equal(config.frameAncestors, "'none'");
});

test("fixed release environment names are accepted", () => {
  const config = loadConfig({
    ...baseEnvironment,
    RELEASE_GITHUB_APP_ID: "12345",
    RELEASE_GITHUB_APP_INSTALLATION_ID: "67890",
    RELEASE_GITHUB_APP_PRIVATE_KEY_B64: privateKeyBase64,
    RELEASE_PRODUCTION_HMAC_SECRET: "h".repeat(32),
    RELEASE_RUNNERS_ISOLATED: "true",
    RELEASE_CLEAN_BATCH_DATABASE_READY: "true",
    RELEASE_AGENT_PORT: "3100",
    RELEASE_AGENT_STATE_FILE: "/data/custom-state.json",
    RELEASE_CONTROL_FRAME_ANCESTORS: "http://100.100.226.27:18080 https://example.com",
  });
  assert.equal(config.configured, true);
  assert.equal(config.port, 3100);
  assert.equal(config.stateFile, "/data/custom-state.json");
  assert.equal(config.testStatusFile, "/test-status/current-build-ref");
  assert.equal(config.workflowRunTimeoutMs, 300_000);
  assert.equal(config.frameAncestors, "http://100.100.226.27:18080 https://example.com");
});

test("configured GitHub App requires a separate production HMAC secret", () => {
  assert.throws(
    () => loadConfig({
      ...baseEnvironment,
      RELEASE_GITHUB_APP_ID: "12345",
      RELEASE_GITHUB_APP_INSTALLATION_ID: "67890",
      RELEASE_GITHUB_APP_PRIVATE_KEY_B64: privateKeyBase64,
      RELEASE_RUNNERS_ISOLATED: "true",
      RELEASE_CLEAN_BATCH_DATABASE_READY: "true",
    }),
    /RELEASE_PRODUCTION_HMAC_SECRET/,
  );
});

test("GitHub App release access stays disabled until both host safety gates are true", () => {
  assert.throws(
    () => loadConfig({
      ...baseEnvironment,
      RELEASE_GITHUB_APP_ID: "12345",
      RELEASE_GITHUB_APP_INSTALLATION_ID: "67890",
      RELEASE_GITHUB_APP_PRIVATE_KEY_B64: privateKeyBase64,
      RELEASE_PRODUCTION_HMAC_SECRET: "h".repeat(32),
      RELEASE_RUNNERS_ISOLATED: "true",
    }),
    /clean batch database/,
  );
});

test("partial GitHub App configuration is rejected", () => {
  assert.throws(
    () => loadConfig({ ...baseEnvironment, RELEASE_GITHUB_APP_ID: "12345" }),
    /GitHub App configuration/,
  );
});

test("admin password must be high entropy length", () => {
  assert.throws(
    () => loadConfig({ ...baseEnvironment, RELEASE_ADMIN_PASSWORD: "too-short" }),
    /RELEASE_ADMIN_PASSWORD/,
  );
});

test("frame ancestor configuration only accepts origins", () => {
  assert.throws(
    () => loadConfig({ ...baseEnvironment, RELEASE_CONTROL_FRAME_ANCESTORS: "https://example.com/path" }),
    /RELEASE_CONTROL_FRAME_ANCESTORS/,
  );
});
