import { createSign } from "node:crypto";

const API_BASE = "https://api.github.com";
const TOKEN_REFRESH_WINDOW_MS = 5 * 60 * 1000;

function base64Url(value) {
  return Buffer.from(value).toString("base64url");
}

export class GitHubAppTokenProvider {
  constructor({ appId, installationId, privateKey, fetchImpl = globalThis.fetch, now = () => Date.now() }) {
    this.appId = appId;
    this.installationId = installationId;
    this.privateKey = privateKey;
    this.fetchImpl = fetchImpl;
    this.now = now;
    this.cached = null;
    this.refreshing = null;
  }

  createJwt() {
    const issuedAt = Math.floor(this.now() / 1000) - 60;
    const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
    const payload = base64Url(JSON.stringify({ iat: issuedAt, exp: issuedAt + 9 * 60, iss: this.appId }));
    const unsigned = `${header}.${payload}`;
    const signer = createSign("RSA-SHA256");
    signer.update(unsigned);
    signer.end();
    return `${unsigned}.${signer.sign(this.privateKey, "base64url")}`;
  }

  async refresh() {
    const response = await this.fetchImpl(
      `${API_BASE}/app/installations/${encodeURIComponent(this.installationId)}/access_tokens`,
      {
        method: "POST",
        headers: {
          Accept: "application/vnd.github+json",
          Authorization: `Bearer ${this.createJwt()}`,
          "Content-Type": "application/json",
          "User-Agent": "procurement-release-agent",
          "X-GitHub-Api-Version": "2022-11-28",
        },
        body: "{}",
        signal: AbortSignal.timeout(20_000),
      },
    );
    if (!response.ok) {
      try {
        await response.body?.cancel();
      } catch {
        // Authentication responses are never included in logs or errors.
      }
      throw new Error(`GitHub App token request failed (${response.status})`);
    }
    const payload = await response.json();
    const expiresAt = Date.parse(payload?.expires_at);
    if (typeof payload?.token !== "string" || payload.token.length < 20 || !Number.isFinite(expiresAt)) {
      throw new Error("GitHub App returned an invalid installation token");
    }
    this.cached = { token: payload.token, expiresAt };
    return payload.token;
  }

  async getToken() {
    if (this.cached && this.cached.expiresAt - this.now() > TOKEN_REFRESH_WINDOW_MS) {
      return this.cached.token;
    }
    if (!this.refreshing) {
      this.refreshing = this.refresh().finally(() => {
        this.refreshing = null;
      });
    }
    return this.refreshing;
  }
}
