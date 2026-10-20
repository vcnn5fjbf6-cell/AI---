import { loadConfig } from "./config.js";
import { GitHubAppTokenProvider } from "./github-app-auth.js";
import { GitHubClient } from "./github-client.js";
import { ReleaseService } from "./release-service.js";
import { createReleaseAgentServer } from "./server.js";
import { AtomicStateStore } from "./state-store.js";

const config = loadConfig();
let github = null;
if (config.configured) {
  const tokenProvider = new GitHubAppTokenProvider({
    appId: config.githubAppId,
    installationId: config.githubAppInstallationId,
    privateKey: config.githubAppPrivateKey,
  });
  github = new GitHubClient({
    owner: config.owner,
    repo: config.repo,
    tokenProvider: () => tokenProvider.getToken(),
  });
}
const service = new ReleaseService({
  github,
  store: new AtomicStateStore(config.stateFile),
  config,
  pollIntervalMs: config.pollIntervalMs,
});

await service.init();

const server = createReleaseAgentServer({
  service,
  adminUsername: config.adminUsername,
  adminPassword: config.adminPassword,
  frameAncestors: config.frameAncestors,
});

server.listen(config.port, "0.0.0.0", () => {
  console.log(`Release agent listening on port ${config.port}`);
});

function shutdown() {
  service.close();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 10_000).unref();
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
