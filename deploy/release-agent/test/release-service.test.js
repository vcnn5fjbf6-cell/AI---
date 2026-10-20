import assert from "node:assert/strict";
import { createHmac } from "node:crypto";
import test from "node:test";

import { GitHubError } from "../src/errors.js";
import { ReleaseService } from "../src/release-service.js";
import {
  CONFIG,
  FakeGitHub,
  IMAGE,
  MemoryStore,
  REQUESTED_BY,
  SHA,
  testDeploymentMarker,
  testRun,
} from "../test-support/helpers.js";

const BATCH_ID = "123e4567-e89b-42d3-a456-426614174000";

async function fixture(overrides = {}) {
  const github = new FakeGitHub();
  const service = new ReleaseService({
    github,
    store: new MemoryStore(),
    config: CONFIG,
    now: () => new Date("2026-07-14T00:00:00.000Z"),
    idFactory: () => BATCH_ID,
    readTestBuildRef: async () => testDeploymentMarker(),
    autoPoll: false,
    ...overrides,
  });
  await service.init();
  return { github, service };
}

const createInput = {
  prNumbers: [7],
  idempotencyKey: "create:batch-7",
  requestedBy: REQUESTED_BY,
};

const promoteInput = {
  confirmation: "DEPLOY_PRODUCTION",
  idempotencyKey: "promote:batch-7",
  requestedBy: REQUESTED_BY,
};

function successfulBatchTestRun(batch, id = 10) {
  return testRun({
    id,
    sha: SHA.main,
    branch: CONFIG.mainBranch,
    displayTitle: `Release test ${batch.id} ${batch.releaseSha}`,
  });
}

test("batch moves through TESTING, READY, PROMOTING and RELEASED", async () => {
  const { github, service } = await fixture();
  const created = await service.createBatch(createInput);
  assert.equal(created.batch.status, "TESTING");
  assert.equal(created.batch.releaseBranch, `release/batch-${BATCH_ID}`);
  assert.deepEqual(github.createdBranch, { branch: created.batch.releaseBranch, sha: SHA.main });
  assert.equal(created.batch.pullRequests[0].statusId, 501);
  assert.equal(created.batch.pullRequests[0].statusTargetUrl, "https://github.example/actions/runs/501");
  assert.deepEqual(github.statusQueries.at(-1), {
    sha: SHA.head,
    context: `Verify pull request build @ ${SHA.main}`,
  });
  assert.deepEqual(github.dispatched[0], {
    workflow: CONFIG.testWorkflow,
    ref: CONFIG.mainBranch,
    inputs: {
      candidate_ref: created.batch.releaseBranch,
      candidate_sha: SHA.release,
      release_batch_id: BATCH_ID,
    },
  });

  github.testRuns = [successfulBatchTestRun(created.batch)];
  await service.pollOnce();
  assert.equal(service.getBatches()[0].status, "READY");

  const promoted = await service.promoteBatch(BATCH_ID, promoteInput);
  assert.equal(promoted.batch.status, "PROMOTING");
  assert.deepEqual(github.dispatched[1], {
    workflow: CONFIG.prodWorkflow,
    ref: CONFIG.mainBranch,
    inputs: {
      confirm: "DEPLOY_PRODUCTION",
      release_sha: SHA.release,
      release_batch_id: BATCH_ID,
      test_run_id: "10",
      backend_image: IMAGE.backend,
      frontend_image: IMAGE.frontend,
      release_signature: createHmac("sha256", CONFIG.productionHmacSecret)
        .update(`${BATCH_ID}:${SHA.release}:${IMAGE.backend}:${IMAGE.frontend}:10`)
        .digest("hex"),
    },
  });
  assert.deepEqual(github.fastForwards, [{ branch: CONFIG.mainBranch, sha: SHA.release, force: false }]);
  assert.deepEqual(github.closedPullRequests, [42]);
  assert.equal(promoted.batch.productionSha, SHA.release);
  assert.equal(promoted.batch.backendImage, IMAGE.backend);
  assert.equal(promoted.batch.frontendImage, IMAGE.frontend);
  const persistedState = JSON.stringify(service.state);
  assert.equal(persistedState.includes(CONFIG.productionHmacSecret), false);
  assert.equal(persistedState.includes(github.dispatched[1].inputs.release_signature), false);

  github.productionRuns = [testRun({
    id: 11,
    sha: SHA.release,
    branch: CONFIG.mainBranch,
    displayTitle: `Production ${BATCH_ID} ${SHA.release}`,
  })];
  await service.pollOnce();
  assert.equal(service.getBatches()[0].status, "RELEASED");
});

test("context returns configured release candidates with readiness details", async () => {
  const { github, service } = await fixture();
  const context = await service.getContext();
  assert.equal(context.configured, true);
  assert.equal(context.reason, null);
  assert.equal(context.activeBatch, null);
  assert.deepEqual(context.candidates[0], {
    number: 7,
    title: "Feature 7",
    body: "Candidate body",
    url: "https://github.example/pull/7",
    author: "developer",
    headBranch: "feature/7",
    headSha: SHA.head,
    mergeableState: "clean",
    testStatus: "SUCCESS",
    testUrl: "https://github.example/actions/runs/501",
    ready: true,
    blockedReason: null,
    changedFiles: 3,
    additions: 20,
    deletions: 5,
    modules: [
      { code: "shared", label: "通用基础" },
      { code: "docs", label: "文档说明" },
    ],
    files: [
      "backend-springboot/src/main/java/example/Feature.java",
      "frontend-vue/src/pages/example/Feature.vue",
      "docs/feature.md",
    ],
    fileListTruncated: false,
    protectedFiles: [],
  });
  assert.deepEqual(github.statusQueries[0], {
    sha: SHA.head,
    context: `Verify pull request build @ ${SHA.main}`,
  });
});

test("release candidates are grouped by changed business modules", async () => {
  const { github, service } = await fixture();
  const baseGetPullRequest = github.getPullRequest.bind(github);
  github.getPullRequest = async (number) => ({
    ...await baseGetPullRequest(number),
    changed_files: 4,
  });
  github.listPullRequestFiles = async () => [
    { filename: "backend-springboot/src/main/java/com/company/procurement/modules/procurement/request/PurchaseRequestService.java" },
    { filename: "frontend-vue/src/pages/platform/DisasterWeatherPage.vue" },
    { filename: "backend-springboot/sql/zz_20260715_procurement.sql" },
    { filename: "docs/RELEASE_CONTROL_OPERATOR_GUIDE.md" },
  ];
  const context = await service.getContext();
  assert.deepEqual(context.candidates[0].modules, [
    { code: "procurement", label: "采购管理" },
    { code: "disaster", label: "灾害预警" },
    { code: "database", label: "数据库脚本" },
    { code: "docs", label: "文档说明" },
  ]);

  const created = await service.createBatch(createInput);
  assert.deepEqual(created.batch.modules, context.candidates[0].modules);
  assert.match(github.releasePullInput.body, /Modules: 采购管理, 灾害预警, 数据库脚本, 文档说明/);
});

test("missing GitHub App keeps context available but rejects writes", async () => {
  const service = new ReleaseService({
    github: null,
    store: new MemoryStore(),
    config: { ...CONFIG, configurationReason: "未配置仓库专属 GitHub App" },
    autoPoll: false,
  });
  await service.init();
  const context = await service.getContext();
  assert.equal(context.configured, false);
  assert.equal(context.reason, "未配置仓库专属 GitHub App");
  assert.deepEqual(context.candidates, []);
  await assert.rejects(
    service.createBatch(createInput),
    (error) => error.code === "RELEASE_AGENT_NOT_CONFIGURED",
  );
});

test("idempotency replays the same batch and rejects a changed request", async () => {
  const { service } = await fixture();
  const first = await service.createBatch(createInput);
  const replay = await service.createBatch(createInput);
  assert.equal(replay.replayed, true);
  assert.equal(replay.batch.id, first.batch.id);
  await assert.rejects(
    service.createBatch({ ...createInput, prNumbers: [8] }),
    (error) => error.code === "IDEMPOTENCY_CONFLICT",
  );
});

test("only one active batch is allowed", async () => {
  const { service } = await fixture();
  await service.createBatch(createInput);
  await assert.rejects(
    service.createBatch({ ...createInput, idempotencyKey: "create:batch-8", prNumbers: [8] }),
    (error) => error.code === "ACTIVE_BATCH_EXISTS",
  );
});

test("promotion refuses a batch when main changed after testing", async () => {
  const { github, service } = await fixture();
  const created = await service.createBatch(createInput);
  github.testRuns = [successfulBatchTestRun(created.batch)];
  await service.pollOnce();
  github.mainSha = SHA.changedMain;
  await assert.rejects(
    service.promoteBatch(BATCH_ID, promoteInput),
    (error) => error.code === "MAIN_BRANCH_CHANGED",
  );
  assert.equal(service.getBatches()[0].status, "STALE");
  assert.equal(service.getBatches()[0].failureCode, "MAIN_BRANCH_CHANGED");
});

test("PRs must be open, clean, non-draft and have the exact successful base-bound status", async () => {
  const { github, service } = await fixture();
  github.getHeadStatus = async () => ({ id: 1, state: "failure", creator: { login: "github-actions[bot]" } });
  await assert.rejects(
    service.createBatch(createInput),
    (error) => error.code === "PR_TEST_NOT_SUCCESSFUL",
  );
  assert.equal(service.getBatches().length, 0);
});

test("PR verification rejects a success status created for an older main SHA", async () => {
  const { github, service } = await fixture();
  github.mainSha = SHA.changedMain;
  github.getHeadStatus = async (_sha, context) => context.endsWith(SHA.main)
    ? { id: 501, state: "success", target_url: "https://github.example/actions/runs/501", creator: { login: "github-actions[bot]" } }
    : null;
  await assert.rejects(
    service.createBatch(createInput),
    (error) => error.code === "PR_TEST_NOT_SUCCESSFUL",
  );
  assert.equal(service.getBatches().length, 0);
});

test("PR verification rejects a matching status not created by GitHub Actions", async () => {
  const { github, service } = await fixture();
  github.getHeadStatus = async (_sha, context) => ({
    id: 900,
    context,
    state: "success",
    target_url: "https://github.example/actions/runs/900",
    creator: { login: "repository-writer" },
  });
  await assert.rejects(
    service.createBatch(createInput),
    (error) => error.code === "PR_TEST_NOT_SUCCESSFUL",
  );
});

test("protected release infrastructure cannot enter an automatic batch", async () => {
  const { github, service } = await fixture();
  github.getPullRequest = async (number) => ({
    ...(await FakeGitHub.prototype.getPullRequest.call(github, number)),
    changed_files: 2,
  });
  github.listPullRequestFiles = async () => [
    {
      filename: "docs/renamed-deploy.yml",
      previousFilename: ".github/workflows/deploy.yml",
    },
    { filename: ".workflow/gitee-test-deploy.yml" },
  ];

  const context = await service.getContext();
  assert.equal(context.candidates[0].ready, false);
  assert.match(context.candidates[0].blockedReason, /发布基础设施/);
  assert.deepEqual(context.candidates[0].protectedFiles, [
    ".github/workflows/deploy.yml",
    ".workflow/gitee-test-deploy.yml",
  ]);
  await assert.rejects(
    service.createBatch(createInput),
    (error) => error.code === "PR_PROTECTED_PATHS",
  );
});

test("production polling ignores another batch run for the same SHA", async () => {
  const { github, service } = await fixture();
  const created = await service.createBatch(createInput);
  github.testRuns = [successfulBatchTestRun(created.batch)];
  await service.pollOnce();
  await service.promoteBatch(BATCH_ID, promoteInput);
  github.productionRuns = [testRun({
    id: 12,
    sha: SHA.release,
    branch: CONFIG.mainBranch,
    displayTitle: `Production 223e4567-e89b-42d3-a456-426614174000 ${SHA.release}`,
  })];

  await service.pollOnce();
  assert.equal(service.getBatches()[0].status, "PROMOTING");
  assert.equal(service.getBatches()[0].productionRunId, null);
});

test("a successful workflow is not READY when the trusted test marker was replaced", async () => {
  const { github, service } = await fixture({
    readTestBuildRef: async () => testDeploymentMarker(SHA.changedMain),
  });
  const created = await service.createBatch(createInput);
  github.testRuns = [successfulBatchTestRun(created.batch)];
  await service.pollOnce();
  assert.equal(service.getBatches()[0].status, "TEST_FAILED");
  assert.equal(service.getBatches()[0].failureCode, "TEST_ENVIRONMENT_REPLACED");
});

test("promotion rechecks the trusted test marker immediately before changing main", async () => {
  let marker = testDeploymentMarker();
  const { github, service } = await fixture({ readTestBuildRef: async () => marker });
  const created = await service.createBatch(createInput);
  github.testRuns = [successfulBatchTestRun(created.batch)];
  await service.pollOnce();
  marker = testDeploymentMarker(SHA.changedMain);
  await assert.rejects(
    service.promoteBatch(BATCH_ID, promoteInput),
    (error) => error.code === "TEST_ENVIRONMENT_REPLACED",
  );
  assert.equal(service.getBatches()[0].status, "TEST_FAILED");
  assert.equal(github.fastForwards.length, 0);
});

test("promotion rejects an incomplete test run before changing main", async () => {
  const { github, service } = await fixture();
  const created = await service.createBatch(createInput);
  github.testRuns = [successfulBatchTestRun(created.batch)];
  await service.pollOnce();
  service.state.batches[0].test.runId = null;

  await assert.rejects(
    service.promoteBatch(BATCH_ID, promoteInput),
    (error) => error.code === "TEST_RUN_UNAVAILABLE",
  );
  assert.equal(service.getBatches()[0].status, "READY");
  assert.equal(github.fastForwards.length, 0);
  assert.equal(github.closedPullRequests.length, 0);
});

test("promotion rejects a different image digest even when the tested commit is unchanged", async () => {
  let marker = testDeploymentMarker();
  const { github, service } = await fixture({ readTestBuildRef: async () => marker });
  const created = await service.createBatch(createInput);
  github.testRuns = [successfulBatchTestRun(created.batch)];
  await service.pollOnce();
  marker = JSON.stringify({
    ...JSON.parse(marker),
    backendImage: IMAGE.backend.replace(/1{64}$/, "3".repeat(64)),
  });
  await assert.rejects(
    service.promoteBatch(BATCH_ID, promoteInput),
    (error) => error.code === "TEST_ENVIRONMENT_REPLACED",
  );
  assert.equal(service.getBatches()[0].status, "TEST_FAILED");
  assert.equal(github.fastForwards.length, 0);
});

test("promotion detects a branch race after fast-forward and never dispatches production", async () => {
  const { github, service } = await fixture();
  const created = await service.createBatch(createInput);
  github.testRuns = [successfulBatchTestRun(created.batch)];
  await service.pollOnce();
  github.updateBranchRefFastForward = async (branch, sha) => {
    github.fastForwards.push({ branch, sha, force: false });
    github.mainSha = SHA.changedMain;
    return sha;
  };
  await assert.rejects(
    service.promoteBatch(BATCH_ID, promoteInput),
    (error) => error.code === "MAIN_BRANCH_RACE",
  );
  const batch = service.getBatches()[0];
  assert.equal(batch.status, "PROD_FAILED");
  assert.equal(batch.productionSha, SHA.release);
  assert.equal(github.dispatched.filter((item) => item.workflow === CONFIG.prodWorkflow).length, 0);
});

test("missing workflow runs time out for both test and production phases", async () => {
  let now = new Date("2026-07-14T00:00:00.000Z");
  const first = await fixture({ now: () => now, workflowRunTimeoutMs: 300_000 });
  await first.service.createBatch(createInput);
  now = new Date("2026-07-14T00:05:01.000Z");
  await first.service.pollOnce();
  assert.equal(first.service.getBatches()[0].status, "TEST_FAILED");
  assert.equal(first.service.getBatches()[0].failureCode, "TEST_WORKFLOW_NOT_FOUND_TIMEOUT");

  now = new Date("2026-07-14T00:00:00.000Z");
  const second = await fixture({ now: () => now, workflowRunTimeoutMs: 300_000 });
  const created = await second.service.createBatch(createInput);
  second.github.testRuns = [successfulBatchTestRun(created.batch)];
  await second.service.pollOnce();
  await second.service.promoteBatch(BATCH_ID, promoteInput);
  now = new Date("2026-07-14T00:05:01.000Z");
  await second.service.pollOnce();
  assert.equal(second.service.getBatches()[0].status, "PROD_FAILED");
  assert.equal(second.service.getBatches()[0].failureCode, "PRODUCTION_WORKFLOW_NOT_FOUND_TIMEOUT");
  await assert.rejects(
    second.service.createBatch({ ...createInput, idempotencyKey: "create:blocked-after-prod-failure" }),
    (error) => error.code === "ACTIVE_BATCH_EXISTS",
  );
});

test("safe retry resumes after dispatch failure when main already equals productionSha", async () => {
  const { github, service } = await fixture();
  const created = await service.createBatch(createInput);
  github.testRuns = [successfulBatchTestRun(created.batch)];
  await service.pollOnce();

  const dispatch = github.dispatchWorkflow.bind(github);
  let failProductionOnce = true;
  github.dispatchWorkflow = async (workflow, ref, inputs) => {
    if (workflow === CONFIG.prodWorkflow && failProductionOnce) {
      failProductionOnce = false;
      throw new GitHubError(502, "dispatch workflow");
    }
    return dispatch(workflow, ref, inputs);
  };
  await assert.rejects(service.promoteBatch(BATCH_ID, promoteInput));
  assert.equal(service.getBatches()[0].status, "PROD_FAILED");
  assert.equal(github.mainSha, SHA.release);

  const retried = await service.retryBatch(BATCH_ID, {
    ...promoteInput,
    idempotencyKey: "retry:production-dispatch",
  });
  assert.equal(retried.batch.status, "PROMOTING");
  assert.equal(github.fastForwards.length, 1);
  assert.equal(github.dispatched.filter((item) => item.workflow === CONFIG.prodWorkflow).length, 1);
});

test("safe retry fast-forwards from base after an earlier GitHub update failure", async () => {
  const { github, service } = await fixture();
  const created = await service.createBatch(createInput);
  github.testRuns = [successfulBatchTestRun(created.batch)];
  await service.pollOnce();

  const update = github.updateBranchRefFastForward.bind(github);
  let failUpdateOnce = true;
  github.updateBranchRefFastForward = async (...args) => {
    if (failUpdateOnce) {
      failUpdateOnce = false;
      throw new GitHubError(503, "fast-forward main branch");
    }
    return update(...args);
  };
  await assert.rejects(service.promoteBatch(BATCH_ID, promoteInput));
  assert.equal(service.getBatches()[0].status, "PROD_FAILED");
  assert.equal(github.mainSha, SHA.main);

  const retried = await service.retryBatch(BATCH_ID, {
    ...promoteInput,
    idempotencyKey: "retry:main-base",
  });
  assert.equal(retried.batch.status, "PROMOTING");
  assert.equal(github.mainSha, SHA.release);
});

test("retry refuses to run after main leaves both safe commits", async () => {
  const { github, service } = await fixture();
  const created = await service.createBatch(createInput);
  github.testRuns = [successfulBatchTestRun(created.batch)];
  await service.pollOnce();
  github.dispatchWorkflow = async (workflow) => {
    if (workflow === CONFIG.prodWorkflow) throw new GitHubError(502, "dispatch workflow");
  };
  await assert.rejects(service.promoteBatch(BATCH_ID, promoteInput));
  github.mainSha = SHA.changedMain;
  await assert.rejects(
    service.retryBatch(BATCH_ID, { ...promoteInput, idempotencyKey: "retry:unsafe-main" }),
    (error) => error.code === "PRODUCTION_RETRY_UNSAFE",
  );
  assert.equal(service.getBatches()[0].status, "PROD_FAILED");
});
