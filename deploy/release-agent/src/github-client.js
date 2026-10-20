import { GitHubError } from "./errors.js";
import { validateSha } from "./validation.js";

const API_BASE = "https://api.github.com";

function refPath(branch) {
  return branch.split("/").map(encodeURIComponent).join("/");
}

export class GitHubClient {
  constructor({ owner, repo, token = null, tokenProvider = null, fetchImpl = globalThis.fetch }) {
    if (!tokenProvider && !token) {
      throw new Error("GitHub authentication is required");
    }
    this.owner = owner;
    this.repo = repo;
    this.tokenProvider = tokenProvider || (async () => token);
    this.fetchImpl = fetchImpl;
    this.repoPath = `/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`;
  }

  async request(operation, method, path, body) {
    const token = await this.tokenProvider();
    const response = await this.fetchImpl(`${API_BASE}${this.repoPath}${path}`, {
      method,
      headers: {
        Accept: "application/vnd.github+json",
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
        "User-Agent": "procurement-release-agent",
        "X-GitHub-Api-Version": "2022-11-28",
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(20_000),
    });
    if (!response.ok) {
      try {
        await response.body?.cancel();
      } catch {
        // The response body is never included in errors or logs.
      }
      throw new GitHubError(response.status, operation);
    }
    if (response.status === 204) {
      return { status: response.status, data: null };
    }
    return { status: response.status, data: await response.json() };
  }

  async getBranchSha(branch) {
    const { data } = await this.request("get branch", "GET", `/git/ref/heads/${refPath(branch)}`);
    return validateSha(data?.object?.sha, "branch SHA");
  }

  async getPullRequest(number) {
    const { data } = await this.request("get pull request", "GET", `/pulls/${number}`);
    return data;
  }

  async listPullRequestFiles(number) {
    const files = [];
    for (let page = 1; page <= 30; page += 1) {
      const query = new URLSearchParams({
        per_page: "100",
        page: String(page),
      });
      const { data } = await this.request(
        "list pull request files",
        "GET",
        `/pulls/${number}/files?${query}`,
      );
      const pageFiles = data || [];
      files.push(...pageFiles.map((file) => ({
        filename: String(file?.filename || ""),
        previousFilename: file?.previous_filename ? String(file.previous_filename) : null,
      })));
      if (pageFiles.length < 100) {
        return files;
      }
    }
    throw new GitHubError(422, "inspect pull request files");
  }

  async listOpenPullRequests(mainBranch) {
    const query = new URLSearchParams({
      state: "open",
      base: mainBranch,
      sort: "updated",
      direction: "desc",
      per_page: "100",
    });
    const { data } = await this.request("list open pull requests", "GET", `/pulls?${query}`);
    return data || [];
  }

  async getHeadStatus(sha, context) {
    validateSha(sha);
    const query = new URLSearchParams({ per_page: "100" });
    const { data } = await this.request("get commit statuses", "GET", `/commits/${sha}/statuses?${query}`);
    return (data || [])
      .filter((status) => status.context === context)
      .sort((left, right) => Number(right.id) - Number(left.id))[0] || null;
  }

  async createBranch(branch, sha) {
    validateSha(sha);
    await this.request("create release branch", "POST", "/git/refs", {
      ref: `refs/heads/${branch}`,
      sha,
    });
  }

  async mergeCommit(branch, headSha, message) {
    validateSha(headSha);
    const { status, data } = await this.request("merge pull request head", "POST", "/merges", {
      base: branch,
      head: headSha,
      commit_message: message,
    });
    if (status === 204) {
      return this.getBranchSha(branch);
    }
    return validateSha(data?.sha, "release branch SHA");
  }

  async createReleasePull({ branch, mainBranch, title, body }) {
    const { data } = await this.request("create release pull request", "POST", "/pulls", {
      head: branch,
      base: mainBranch,
      title,
      body,
      draft: false,
    });
    return {
      number: data?.number,
      url: data?.html_url,
    };
  }

  async dispatchWorkflow(workflow, ref, inputs = undefined) {
    const body = { ref };
    if (inputs !== undefined) {
      body.inputs = inputs;
    }
    await this.request(
      "dispatch workflow",
      "POST",
      `/actions/workflows/${encodeURIComponent(workflow)}/dispatches`,
      body,
    );
  }

  async listWorkflowRuns(workflow, branch, displayTitle = null) {
    const query = new URLSearchParams({
      branch,
      event: "workflow_dispatch",
      per_page: "30",
    });
    const { data } = await this.request(
      "list workflow runs",
      "GET",
      `/actions/workflows/${encodeURIComponent(workflow)}/runs?${query}`,
    );
    const runs = data?.workflow_runs || [];
    return displayTitle === null
      ? runs
      : runs.filter((run) => run.display_title === displayTitle);
  }

  async updateBranchRefFastForward(branch, sha) {
    validateSha(sha);
    const { data } = await this.request("fast-forward main branch", "PATCH", `/git/refs/heads/${refPath(branch)}`, {
      sha,
      force: false,
    });
    const updatedSha = validateSha(data?.object?.sha, "updated branch SHA");
    if (updatedSha !== sha) {
      throw new GitHubError(409, "verify fast-forwarded main branch");
    }
    return updatedSha;
  }

  async closePullRequest(number) {
    if (!Number.isSafeInteger(number) || number < 1) {
      throw new Error("Pull request number is invalid");
    }
    await this.request("close release pull request", "PATCH", `/pulls/${number}`, {
      state: "closed",
    });
  }
}
