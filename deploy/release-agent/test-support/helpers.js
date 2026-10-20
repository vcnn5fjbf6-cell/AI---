import { initialState } from "../src/state-store.js";

export const SHA = {
  main: "a".repeat(40),
  head: "b".repeat(40),
  release: "c".repeat(40),
  production: "d".repeat(40),
  changedMain: "e".repeat(40),
};

export const IMAGE = Object.freeze({
  backend: `ghcr.io/example-owner/example-repo-backend@sha256:${"1".repeat(64)}`,
  frontend: `ghcr.io/example-owner/example-repo-frontend@sha256:${"2".repeat(64)}`,
});

export function testDeploymentMarker(buildRef = SHA.release) {
  return JSON.stringify({
    buildRef,
    backendImage: IMAGE.backend,
    frontendImage: IMAGE.frontend,
  });
}

export const REQUESTED_BY = Object.freeze({
  userId: 1001,
  username: "release-admin",
  realName: "发布管理员",
  clientIp: "192.0.2.10",
  userAgent: "node-test",
});

export const CONFIG = Object.freeze({
  owner: "example-owner",
  repo: "example-repo",
  mainBranch: "main",
  testWorkflow: "deploy-test.yml",
  prodWorkflow: "deploy.yml",
  productionHmacSecret: "h".repeat(32),
  testStatusFile: "/test-status/current-build-ref",
  workflowRunTimeoutMs: 300_000,
});

export class MemoryStore {
  constructor(state = initialState()) {
    this.state = structuredClone(state);
  }

  async load() {
    return structuredClone(this.state);
  }

  async save(state) {
    this.state = structuredClone(state);
  }
}

export class FakeGitHub {
  constructor() {
    this.mainSha = SHA.main;
    this.dispatched = [];
    this.testRuns = [];
    this.productionRuns = [];
    this.fastForwards = [];
    this.closedPullRequests = [];
    this.statusQueries = [];
  }

  async getBranchSha() {
    return this.mainSha;
  }

  async getPullRequest(number) {
    return {
      number,
      state: "open",
      draft: false,
      mergeable: true,
      mergeable_state: "clean",
      base: { ref: "main" },
      head: { sha: SHA.head, ref: `feature/${number}` },
      title: `Feature ${number}`,
      html_url: `https://github.example/pull/${number}`,
      body: "Candidate body",
      user: { login: "developer" },
      changed_files: 3,
      additions: 20,
      deletions: 5,
    };
  }

  async listOpenPullRequests() {
    return [{ number: 7, head: { ref: "feature/seven" } }];
  }

  async listPullRequestFiles() {
    return [
      { filename: "backend-springboot/src/main/java/example/Feature.java", previousFilename: null },
      { filename: "frontend-vue/src/pages/example/Feature.vue", previousFilename: null },
      { filename: "docs/feature.md", previousFilename: null },
    ];
  }

  async getHeadStatus(sha, context) {
    this.statusQueries.push({ sha, context });
    return {
      id: 501,
      context,
      state: "success",
      target_url: "https://github.example/actions/runs/501",
      creator: { login: "github-actions[bot]" },
    };
  }

  async createBranch(branch, sha) {
    this.createdBranch = { branch, sha };
  }

  async mergeCommit() {
    return SHA.release;
  }

  async createReleasePull(input) {
    this.releasePullInput = input;
    return { number: 42, url: "https://github.example/pull/42" };
  }

  async dispatchWorkflow(workflow, ref, inputs) {
    this.dispatched.push({ workflow, ref, inputs });
  }

  async listWorkflowRuns(workflow, branch, displayTitle = null) {
    const runs = workflow === CONFIG.testWorkflow ? this.testRuns : this.productionRuns;
    return runs
      .filter((run) => run.head_branch === branch)
      .filter((run) => displayTitle === null || run.display_title === displayTitle);
  }

  async updateBranchRefFastForward(branch, sha) {
    this.fastForwards.push({ branch, sha, force: false });
    this.mainSha = sha;
    return sha;
  }

  async closePullRequest(number) {
    this.closedPullRequests.push(number);
  }
}

export function testRun({ id, sha, branch, displayTitle = null, conclusion = "success" }) {
  return {
    id,
    event: "workflow_dispatch",
    head_branch: branch,
    head_sha: sha,
    created_at: "2026-07-14T00:00:01.000Z",
    status: "completed",
    conclusion,
    html_url: `https://github.example/actions/runs/${id}`,
    display_title: displayTitle,
  };
}
