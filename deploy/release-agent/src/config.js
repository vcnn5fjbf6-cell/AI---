import path from "node:path";

function required(env, name) {
  const value = env[name]?.trim();
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function fixedName(value, name, pattern) {
  if (
    !pattern.test(value)
    || value.includes("..")
    || value.includes("@{")
    || value.startsWith("/")
    || value.endsWith("/")
  ) {
    throw new Error(`Invalid ${name}`);
  }
  return value;
}

function integer(value, fallback, minimum, maximum, name) {
  const parsed = value === undefined ? fallback : Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`Invalid ${name}`);
  }
  return parsed;
}

function optionalPositiveInteger(env, name) {
  const value = env[name]?.trim();
  if (!value) {
    return null;
  }
  if (!/^[1-9][0-9]*$/.test(value)) {
    throw new Error(`Invalid ${name}`);
  }
  return value;
}

function githubAppPrivateKey(env) {
  const encoded = env.RELEASE_GITHUB_APP_PRIVATE_KEY_B64?.trim();
  if (!encoded) {
    return null;
  }
  if (!/^[A-Za-z0-9+/]+={0,2}$/.test(encoded)) {
    throw new Error("Invalid RELEASE_GITHUB_APP_PRIVATE_KEY_B64");
  }
  const value = Buffer.from(encoded, "base64").toString("utf8");
  if (!/^-----BEGIN (?:RSA )?PRIVATE KEY-----[\s\S]+-----END (?:RSA )?PRIVATE KEY-----\s*$/.test(value)) {
    throw new Error("Invalid RELEASE_GITHUB_APP_PRIVATE_KEY_B64");
  }
  return value;
}

function enabledGate(env, name) {
  const value = env[name]?.trim() || "false";
  if (value !== "true" && value !== "false") {
    throw new Error(`${name} must be true or false`);
  }
  return value === "true";
}

function frameAncestors(env) {
  const value = env.RELEASE_CONTROL_FRAME_ANCESTORS?.trim() || "'none'";
  if (value === "'none'") {
    return value;
  }
  const ancestors = value.split(/\s+/).filter(Boolean);
  if (ancestors.length === 0 || ancestors.length > 8) {
    throw new Error("Invalid RELEASE_CONTROL_FRAME_ANCESTORS");
  }
  for (const ancestor of ancestors) {
    if (ancestor === "'self'") {
      continue;
    }
    try {
      const url = new URL(ancestor);
      if (!["http:", "https:"].includes(url.protocol) || url.pathname !== "/" || url.search || url.hash) {
        throw new Error("invalid origin");
      }
    } catch {
      throw new Error("Invalid RELEASE_CONTROL_FRAME_ANCESTORS");
    }
  }
  return ancestors.join(" ");
}

export function loadConfig(env = process.env) {
  const adminUsername = required(env, "RELEASE_ADMIN_USERNAME");
  if (!/^[A-Za-z0-9._-]{1,64}$/.test(adminUsername)) {
    throw new Error("Invalid RELEASE_ADMIN_USERNAME");
  }
  const adminPassword = required(env, "RELEASE_ADMIN_PASSWORD");
  if (adminPassword.length < 32) {
    throw new Error("RELEASE_ADMIN_PASSWORD must contain at least 32 characters");
  }

  const stateFile = env.RELEASE_AGENT_STATE_FILE?.trim() || "/data/state.json";
  if (!path.isAbsolute(stateFile)) {
    throw new Error("RELEASE_AGENT_STATE_FILE must be an absolute path");
  }
  const githubAppId = optionalPositiveInteger(env, "RELEASE_GITHUB_APP_ID");
  const githubAppInstallationId = optionalPositiveInteger(env, "RELEASE_GITHUB_APP_INSTALLATION_ID");
  const githubAppPrivateKeyValue = githubAppPrivateKey(env);
  const githubAppValues = [githubAppId, githubAppInstallationId, githubAppPrivateKeyValue];
  const githubAppConfigured = githubAppValues.every(Boolean);
  if (!githubAppConfigured && githubAppValues.some(Boolean)) {
    throw new Error("GitHub App configuration must include app ID, installation ID and private key");
  }
  const productionHmacSecret = env.RELEASE_PRODUCTION_HMAC_SECRET?.trim() || null;
  const runnersIsolated = enabledGate(env, "RELEASE_RUNNERS_ISOLATED");
  const cleanBatchDatabaseReady = enabledGate(env, "RELEASE_CLEAN_BATCH_DATABASE_READY");
  if (githubAppConfigured && (!runnersIsolated || !cleanBatchDatabaseReady)) {
    throw new Error("GitHub App release access requires runner isolation and clean batch database verification");
  }
  if (githubAppConfigured && (!productionHmacSecret || productionHmacSecret.length < 32)) {
    throw new Error("RELEASE_PRODUCTION_HMAC_SECRET must contain at least 32 characters when GitHub App is configured");
  }
  const testStatusFile = env.RELEASE_TEST_STATUS_FILE?.trim() || "/test-status/current-build-ref";
  if (!path.isAbsolute(testStatusFile)) {
    throw new Error("RELEASE_TEST_STATUS_FILE must be an absolute path");
  }

  return Object.freeze({
    owner: fixedName(required(env, "RELEASE_GITHUB_OWNER"), "RELEASE_GITHUB_OWNER", /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})$/),
    repo: fixedName(required(env, "RELEASE_GITHUB_REPO"), "RELEASE_GITHUB_REPO", /^[A-Za-z0-9._-]{1,100}$/),
    mainBranch: fixedName(required(env, "RELEASE_GITHUB_MAIN_BRANCH"), "RELEASE_GITHUB_MAIN_BRANCH", /^[A-Za-z0-9._/-]{1,100}$/),
    testWorkflow: fixedName(required(env, "RELEASE_GITHUB_TEST_WORKFLOW"), "RELEASE_GITHUB_TEST_WORKFLOW", /^[A-Za-z0-9._-]+\.ya?ml$/),
    prodWorkflow: fixedName(required(env, "RELEASE_GITHUB_PRODUCTION_WORKFLOW"), "RELEASE_GITHUB_PRODUCTION_WORKFLOW", /^[A-Za-z0-9._-]+\.ya?ml$/),
    githubAppId,
    githubAppInstallationId,
    githubAppPrivateKey: githubAppPrivateKeyValue,
    productionHmacSecret,
    runnersIsolated,
    cleanBatchDatabaseReady,
    configured: githubAppConfigured,
    configurationReason: githubAppConfigured ? null : "未配置仓库专属 GitHub App",
    adminUsername,
    adminPassword,
    port: integer(env.RELEASE_AGENT_PORT, 8080, 1, 65535, "RELEASE_AGENT_PORT"),
    pollIntervalMs: integer(env.RELEASE_AGENT_POLL_INTERVAL_MS, 10_000, 1_000, 60_000, "RELEASE_AGENT_POLL_INTERVAL_MS"),
    workflowRunTimeoutMs: integer(
      env.RELEASE_WORKFLOW_RUN_TIMEOUT_MS,
      300_000,
      60_000,
      3_600_000,
      "RELEASE_WORKFLOW_RUN_TIMEOUT_MS",
    ),
    frameAncestors: frameAncestors(env),
    testStatusFile,
    stateFile,
  });
}
