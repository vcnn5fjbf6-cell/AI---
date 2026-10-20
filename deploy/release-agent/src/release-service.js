import { createHmac, randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";

import { PublicError } from "./errors.js";
import {
  requestHash,
  validateBatchId,
  validateCreateInput,
  validatePromoteInput,
  validateSha,
} from "./validation.js";

const TEST_STATUS_PREFIX = "Verify pull request build";
const TEST_STATUS_CREATOR = "github-actions[bot]";
const PROTECTED_RELEASE_PATHS = [
  ".github/",
  ".workflow/",
  "backend-springboot/.mvn/",
  "backend-springboot/Dockerfile",
  "backend-springboot/pom.xml",
  "backend-springboot/mvnw",
  "backend-springboot/mvnw.cmd",
  "deploy/release-agent/",
  "deploy/actions-runner-job-guard.sh",
  "deploy/verify_release_batch_clean_db.sh",
  "deploy/docker-compose.release-control.yml",
  "deploy/docker-compose.prod.yml",
  "deploy/docker-compose.test.yml",
  "deploy/configure_test_feishu_notifications.sh",
  "deploy/prepare_test_gitee_reconciliation.sh",
  "deploy/deploy_local_runner.sh",
  "deploy/deploy_test_gitee.sh",
  "deploy/deploy_test_runner.sh",
  "deploy/gitee_test_deploy_bootstrap.sh",
  "deploy/install_gitee_test_deploy_cron.sh",
  "deploy/db/test-init/",
  "frontend-vue/deploy/nginx/test.conf",
  "frontend-vue/deploy/nginx/test-feishu-egress.conf",
  "frontend-vue/deploy/nginx/default.conf",
  "frontend-vue/Dockerfile",
  "frontend-vue/package.json",
  "frontend-vue/package-lock.json",
  "frontend-vue/.npmrc",
];
const ACTIVE_STATUSES = new Set(["PREPARING", "TESTING", "READY", "PROMOTING", "PROD_FAILED"]);
const RELEASE_MODULE_RULES = [
  {
    code: "workbench",
    label: "工作台",
    prefixes: [
      "backend-springboot/src/main/java/com/company/procurement/modules/workbench/",
      "frontend-vue/src/pages/Dashboard.vue",
      "frontend-vue/src/pages/dashboard/",
      "frontend-vue/src/pages/workbench",
      "frontend-vue/src/api/dashboard",
      "frontend-vue/src/api/workbench",
      "frontend-vue/src/types/workbench",
    ],
  },
  {
    code: "procurement",
    label: "采购管理",
    prefixes: [
      "backend-springboot/src/main/java/com/company/procurement/modules/procurement/",
      "frontend-vue/src/pages/procurement",
      "frontend-vue/src/pages/procurementVnet",
      "frontend-vue/src/pages/order/",
      "frontend-vue/src/pages/reconciliation/",
      "frontend-vue/src/pages/feishu/FeishuProcurementIntake.vue",
      "frontend-vue/src/api/monthly-procurement",
      "frontend-vue/src/api/order",
      "frontend-vue/src/api/personal-labor",
      "frontend-vue/src/api/procurement",
      "frontend-vue/src/api/purchase-request",
      "frontend-vue/src/api/workflow",
      "frontend-vue/src/types/monthly-procurement",
      "frontend-vue/src/types/order",
      "frontend-vue/src/types/procurement",
      "frontend-vue/src/types/purchase",
    ],
  },
  {
    code: "supplier",
    label: "供应商",
    prefixes: [
      "backend-springboot/src/main/java/com/company/procurement/modules/supplier/",
      "frontend-vue/src/pages/supplier",
      "frontend-vue/src/api/supplier",
      "frontend-vue/src/types/supplier",
    ],
  },
  {
    code: "maintenance",
    label: "维保管理",
    prefixes: [
      "backend-springboot/src/main/java/com/company/procurement/modules/maintenance/",
      "frontend-vue/src/pages/maintenance",
      "frontend-vue/src/api/maintenance",
      "frontend-vue/src/types/maintenance",
    ],
  },
  {
    code: "disaster",
    label: "灾害预警",
    prefixes: [
      "backend-springboot/src/main/java/com/company/procurement/modules/platform/disaster/",
      "frontend-vue/src/pages/disaster/",
      "frontend-vue/src/pages/platform/Disaster",
      "frontend-vue/src/api/disaster-warning",
      "frontend-vue/src/api/disaster",
      "frontend-vue/src/types/disaster-warning",
      "frontend-vue/src/types/disaster",
    ],
  },
  {
    code: "platform",
    label: "平台管理",
    prefixes: [
      "backend-springboot/src/main/java/com/company/procurement/modules/platform/",
      "backend-springboot/src/main/java/com/company/procurement/modules/system/",
      "backend-springboot/src/main/java/com/company/procurement/modules/auth/",
      "backend-springboot/src/main/java/com/company/procurement/modules/approval/",
      "backend-springboot/src/main/java/com/company/procurement/modules/org/",
      "frontend-vue/src/pages/admin/",
      "frontend-vue/src/pages/approval/",
      "frontend-vue/src/pages/login/",
      "frontend-vue/src/pages/platform/",
      "frontend-vue/src/pages/system/",
      "frontend-vue/src/api/approval",
      "frontend-vue/src/api/auth",
      "frontend-vue/src/api/platform",
      "frontend-vue/src/api/role",
      "frontend-vue/src/api/user",
      "frontend-vue/src/config/",
      "frontend-vue/src/router/",
      "frontend-vue/src/types/approval",
      "frontend-vue/src/types/auth",
      "frontend-vue/src/types/platform",
      "frontend-vue/src/types/system",
    ],
    excludePrefixes: [
      "backend-springboot/src/main/java/com/company/procurement/modules/platform/disaster/",
      "frontend-vue/src/pages/disaster/",
      "frontend-vue/src/pages/platform/Disaster",
      "frontend-vue/src/api/disaster-warning",
      "frontend-vue/src/types/disaster-warning",
    ],
  },
  {
    code: "database",
    label: "数据库脚本",
    prefixes: [
      "backend-springboot/sql/",
      "backend-springboot/src/main/resources/db/migration/",
    ],
  },
  {
    code: "release",
    label: "发布基础设施",
    prefixes: [
      ".github/",
      ".workflow/",
      "deploy/",
      "frontend-vue/deploy/",
      "backend-springboot/Dockerfile",
      "frontend-vue/Dockerfile",
    ],
  },
  {
    code: "docs",
    label: "文档说明",
    prefixes: [
      "README.md",
      "docs/",
    ],
  },
];
const FALLBACK_MODULE = Object.freeze({ code: "shared", label: "通用基础" });
const FAILURE_REASONS = {
  AGENT_RESTARTED_DURING_PREPARATION: "发布代理在准备批次时重启",
  TEST_DISPATCH_NOT_RECORDED: "测试 workflow 未完成调度",
  PRODUCTION_DISPATCH_NOT_RECORDED: "生产 workflow 未完成调度",
  BATCH_PREPARATION_FAILED: "发布批次准备失败",
  PRODUCTION_PROMOTION_FAILED: "生产提升启动失败",
  TEST_WORKFLOW_FAILED: "测试 workflow 执行失败",
  PRODUCTION_WORKFLOW_FAILED: "生产 workflow 执行失败",
  TEST_WORKFLOW_NOT_FOUND_TIMEOUT: "测试 workflow 调度后五分钟内未出现运行记录",
  PRODUCTION_WORKFLOW_NOT_FOUND_TIMEOUT: "生产 workflow 调度后五分钟内未出现运行记录",
  MAIN_BRANCH_CHANGED: "main 分支已在测试后变更",
  MAIN_BRANCH_RACE: "main 分支在生产提升期间发生并发变更",
  TEST_ENVIRONMENT_REPLACED: "测试环境当前运行的版本已不是本批次提交",
};

function copy(value) {
  return structuredClone(value);
}

function testStatusContext(baseSha) {
  return `${TEST_STATUS_PREFIX} @ ${validateSha(baseSha, "status base SHA")}`;
}

function changedPaths(files) {
  return files.flatMap((file) => [file.filename, file.previousFilename].filter(Boolean));
}

function protectedReleaseFiles(files) {
  return changedPaths(files).filter((file) => PROTECTED_RELEASE_PATHS.some((protectedPath) => (
    protectedPath.endsWith("/") ? file.startsWith(protectedPath) : file === protectedPath
  )));
}

function matchesReleaseModuleRule(file, rule) {
  if (rule.excludePrefixes?.some((prefix) => (
    prefix.endsWith("/") ? file.startsWith(prefix) : file === prefix || file.startsWith(prefix)
  ))) {
    return false;
  }
  return rule.prefixes.some((prefix) => (
    prefix.endsWith("/") ? file.startsWith(prefix) : file === prefix || file.startsWith(prefix)
  ));
}

function inferReleaseModules(files) {
  const paths = changedPaths(files);
  const matchedRules = RELEASE_MODULE_RULES.filter((rule) => paths.some((file) => matchesReleaseModuleRule(file, rule)));
  const matchedNonDocumentation = matchedRules.some((rule) => rule.code !== "docs");
  const hasUnknownCode = paths.some((file) => (
    !RELEASE_MODULE_RULES.some((rule) => matchesReleaseModuleRule(file, rule))
  ));
  const modules = [];
  if (hasUnknownCode && !matchedNonDocumentation) {
    modules.push(FALLBACK_MODULE);
  }
  modules.push(...matchedRules.map((rule) => ({ code: rule.code, label: rule.label })));
  return modules.length ? modules : [FALLBACK_MODULE];
}

function unionModules(items) {
  const modules = new Map();
  for (const item of items || []) {
    for (const module of item.modules || []) {
      modules.set(module.code, module);
    }
  }
  return [...modules.values()];
}

function publicFileList(files) {
  return {
    files: files.slice(0, 100).map((file) => (
      file.previousFilename ? `${file.previousFilename} → ${file.filename}` : file.filename
    )),
    fileListTruncated: files.length > 100,
  };
}

function expectedImagePrefix(config, component) {
  return `ghcr.io/${config.owner.toLowerCase()}/${config.repo.toLowerCase()}-${component}@sha256:`;
}

function validateDeploymentImage(value, config, component) {
  const prefix = expectedImagePrefix(config, component);
  if (typeof value !== "string" || !value.startsWith(prefix) || !/^[a-f0-9]{64}$/.test(value.slice(prefix.length))) {
    throw new Error(`test ${component} image is not an exact trusted digest`);
  }
  return value;
}

function parseTestDeploymentMarker(rawValue, config) {
  const value = typeof rawValue === "string" ? JSON.parse(rawValue) : rawValue;
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("test deployment marker is invalid");
  }
  return {
    buildRef: validateSha(value.buildRef, "test build marker"),
    backendImage: validateDeploymentImage(value.backendImage, config, "backend"),
    frontendImage: validateDeploymentImage(value.frontendImage, config, "frontend"),
  };
}

function publicFailureCode(error, fallback) {
  if (error instanceof PublicError) {
    return error.code;
  }
  if (error?.name === "GitHubError") {
    return `GITHUB_${error.status || "ERROR"}`;
  }
  return fallback;
}

function publicBatch(batch) {
  return {
    id: batch.id,
    status: batch.status,
    prNumbers: [...batch.prNumbers],
    pullRequests: copy(batch.pullRequests),
    modules: unionModules(batch.pullRequests),
    requestedBy: copy(batch.requestedBy),
    promotedBy: batch.promotedBy ? copy(batch.promotedBy) : null,
    retriedBy: batch.retriedBy ? copy(batch.retriedBy) : null,
    baseSha: batch.baseSha,
    releaseBranch: batch.releaseBranch,
    releaseSha: batch.releaseSha,
    releasePrNumber: batch.releasePrNumber || null,
    releasePrUrl: batch.releasePrUrl || null,
    productionSha: batch.productionSha || null,
    backendImage: batch.backendImage || null,
    frontendImage: batch.frontendImage || null,
    testRunId: batch.test?.runId || null,
    testUrl: batch.test?.runUrl || null,
    testStatus: batch.test?.status || null,
    testConclusion: batch.test?.conclusion || null,
    productionRunId: batch.production?.runId || null,
    productionUrl: batch.production?.runUrl || null,
    productionStatus: batch.production?.status || null,
    productionConclusion: batch.production?.conclusion || null,
    createdAt: batch.createdAt,
    updatedAt: batch.updatedAt,
    testStartedAt: batch.test?.dispatchRequestedAt || null,
    readyAt: batch.readyAt || null,
    promoteRequestedAt: batch.promoteRequestedAt || null,
    retryRequestedAt: batch.retryRequestedAt || null,
    productionStartedAt: batch.production?.dispatchRequestedAt || null,
    releasedAt: batch.releasedAt || null,
    failureCode: batch.failureCode || null,
    failureReason: batch.failureCode
      ? (FAILURE_REASONS[batch.failureCode] || "发布执行失败")
      : null,
  };
}

export class ReleaseService {
  constructor({
    github,
    store,
    config,
    now = () => new Date(),
    idFactory = () => randomUUID(),
    pollIntervalMs = 10_000,
    workflowRunTimeoutMs = config.workflowRunTimeoutMs || 300_000,
    readTestBuildRef,
    autoPoll = true,
    logger = console,
  }) {
    this.github = github;
    this.store = store;
    this.config = config;
    this.now = now;
    this.idFactory = idFactory;
    this.pollIntervalMs = pollIntervalMs;
    this.workflowRunTimeoutMs = workflowRunTimeoutMs;
    this.readTestBuildRef = readTestBuildRef
      || (async () => (await readFile(config.testStatusFile, "utf8")).trim());
    this.autoPoll = autoPoll;
    this.logger = logger;
    this.state = null;
    this.lock = Promise.resolve();
    this.monitorTimer = null;
  }

  async init() {
    this.state = await this.store.load();
    let changed = false;
    for (const batch of this.state.batches) {
      if (batch.status === "PREPARING") {
        batch.status = "TEST_FAILED";
        batch.failureCode = "AGENT_RESTARTED_DURING_PREPARATION";
        batch.updatedAt = this.timestamp();
        changed = true;
      } else if (batch.status === "TESTING" && !batch.test?.dispatchRequestedAt) {
        batch.status = "TEST_FAILED";
        batch.failureCode = "TEST_DISPATCH_NOT_RECORDED";
        batch.updatedAt = this.timestamp();
        changed = true;
      } else if (batch.status === "PROMOTING" && !batch.production?.dispatchRequestedAt) {
        batch.status = "PROD_FAILED";
        batch.failureCode = "PRODUCTION_DISPATCH_NOT_RECORDED";
        batch.updatedAt = this.timestamp();
        changed = true;
      }
    }
    if (changed) {
      await this.save();
    }
    this.scheduleMonitor(0);
  }

  timestamp() {
    return this.now().toISOString();
  }

  async serialized(action) {
    const result = this.lock.then(action, action);
    this.lock = result.catch(() => {});
    return result;
  }

  async save() {
    await this.store.save(this.state);
  }

  activeBatch() {
    return this.state.batches.find((batch) => ACTIVE_STATUSES.has(batch.status)) || null;
  }

  async getContext() {
    let candidates = [];
    let reason = this.config.configurationReason || null;
    if (this.github) {
      try {
        candidates = await this.listCandidates();
      } catch (error) {
        reason = "GitHub API 查询失败";
        this.logger.error("Release candidate query failed", error?.message || error?.name || "unknown error");
      }
    }
    const active = this.activeBatch();
    return {
      configured: Boolean(this.github),
      reason,
      repository: `${this.config.owner}/${this.config.repo}`,
      mainBranch: this.config.mainBranch,
      testWorkflow: this.config.testWorkflow,
      productionWorkflow: this.config.prodWorkflow,
      requiredConfirmation: "DEPLOY_PRODUCTION",
      activeBatch: active ? publicBatch(active) : null,
      candidates,
    };
  }

  getBatches() {
    return [...this.state.batches].reverse().map(publicBatch);
  }

  ensureConfigured() {
    if (!this.github) {
      throw new PublicError(503, "RELEASE_AGENT_NOT_CONFIGURED", "发布代理未配置仓库专属 GitHub App");
    }
  }

  async listCandidates() {
    const currentMainSha = validateSha(await this.github.getBranchSha(this.config.mainBranch), "main SHA");
    const requiredStatusContext = testStatusContext(currentMainSha);
    const summaries = await this.github.listOpenPullRequests(this.config.mainBranch);
    const details = await Promise.all(
      summaries
        .filter((pullRequest) => !String(pullRequest?.head?.ref || "").startsWith("release/batch-"))
        .map((pullRequest) => this.github.getPullRequest(pullRequest.number)),
    );
    return Promise.all(details
      .filter((pullRequest) => pullRequest?.state === "open" && pullRequest?.base?.ref === this.config.mainBranch)
      .map(async (pullRequest) => {
        const headSha = validateSha(pullRequest?.head?.sha, "candidate head SHA");
        const files = await this.github.listPullRequestFiles(pullRequest.number);
        const protectedFiles = protectedReleaseFiles(files);
        const status = await this.github.getHeadStatus(headSha, requiredStatusContext);
        let testStatus = "NOT_RUN";
        if (status?.creator?.login !== TEST_STATUS_CREATOR) {
          testStatus = "NOT_RUN";
        } else if (status.state === "success") {
          testStatus = "SUCCESS";
        } else if (status?.state === "pending") {
          testStatus = "PENDING";
        } else if (status) {
          testStatus = "FAILED";
        }
        let blockedReason = null;
        if (pullRequest.draft === true) {
          blockedReason = "PR 仍为草稿";
        } else if (files.length !== Number(pullRequest.changed_files || 0)) {
          blockedReason = "PR 文件清单尚未稳定，请稍后刷新";
        } else if (protectedFiles.length > 0) {
          blockedReason = "涉及受保护的发布基础设施，必须单独人工审核合并";
        } else if (pullRequest.mergeable !== true || pullRequest.mergeable_state !== "clean") {
          blockedReason = "PR 存在冲突或尚未完成可合并性检查";
        } else if (testStatus === "NOT_RUN") {
          blockedReason = "未找到与当前主线匹配的 PR 构建检查";
        } else if (testStatus === "PENDING") {
          blockedReason = "PR 构建检查进行中";
        } else if (testStatus === "FAILED") {
          blockedReason = "PR 构建检查未通过";
        }
        return {
          number: pullRequest.number,
          title: pullRequest.title || "",
          body: pullRequest.body || "",
          url: pullRequest.html_url,
          author: pullRequest.user?.login || "",
          headBranch: pullRequest.head?.ref || "",
          headSha,
          mergeableState: pullRequest.mergeable_state || "unknown",
          testStatus,
          testUrl: status?.target_url || null,
          ready: blockedReason === null,
          blockedReason,
          changedFiles: Number(pullRequest.changed_files || 0),
          additions: Number(pullRequest.additions || 0),
          deletions: Number(pullRequest.deletions || 0),
          modules: inferReleaseModules(files),
          ...publicFileList(files),
          protectedFiles,
        };
      }));
  }

  getBatch(id) {
    const batch = this.state.batches.find((candidate) => candidate.id === id);
    if (!batch) {
      throw new PublicError(404, "BATCH_NOT_FOUND", "发布批次不存在");
    }
    return batch;
  }

  async createBatch(rawInput) {
    const input = validateCreateInput(rawInput);
    return this.serialized(async () => {
      this.ensureConfigured();
      const hash = requestHash(input);
      const key = `create:${input.idempotencyKey}`;
      const previous = this.state.idempotency[key];
      if (previous) {
        if (previous.requestHash !== hash) {
          throw new PublicError(409, "IDEMPOTENCY_CONFLICT", "幂等键已用于不同请求");
        }
        return { batch: publicBatch(this.getBatch(previous.batchId)), replayed: true };
      }
      if (this.activeBatch()) {
        throw new PublicError(409, "ACTIVE_BATCH_EXISTS", "当前已有活动发布批次");
      }

      const baseSha = validateSha(await this.github.getBranchSha(this.config.mainBranch), "main SHA");
      const pullRequests = await Promise.all(
        input.prNumbers.map((number) => this.verifyPullRequest(number, baseSha)),
      );

      const id = validateBatchId(this.idFactory());
      const releaseBranch = `release/batch-${id}`;
      const createdAt = this.timestamp();
      const batch = {
        id,
        status: "PREPARING",
        requestedBy: copy(input.requestedBy),
        prNumbers: [...input.prNumbers],
        pullRequests,
        baseSha,
        releaseBranch,
        releaseSha: baseSha,
        mergedPrNumbers: [],
        createdAt,
        updatedAt: createdAt,
        test: {},
        production: {},
      };
      this.state.batches.push(batch);
      this.state.idempotency[key] = { batchId: id, requestHash: hash, operation: "create" };
      await this.save();

      try {
        await this.github.createBranch(releaseBranch, baseSha);
        for (const pullRequest of pullRequests) {
          batch.releaseSha = validateSha(await this.github.mergeCommit(
            releaseBranch,
            pullRequest.headSha,
            `release: merge PR #${pullRequest.number} into batch ${id}`,
          ), "release SHA");
          batch.mergedPrNumbers.push(pullRequest.number);
          batch.updatedAt = this.timestamp();
          await this.save();
        }

        const requester = input.requestedBy.realName || input.requestedBy.username;
        const releasePull = await this.github.createReleasePull({
          branch: releaseBranch,
          mainBranch: this.config.mainBranch,
          title: `release: batch ${id}`,
          body: [
            `Release batch: ${id}`,
            `Requested by: ${requester}`,
            `Included PRs: ${input.prNumbers.map((number) => `#${number}`).join(", ")}`,
            `Modules: ${unionModules(batch.pullRequests).map((module) => module.label).join(", ")}`,
            `Base SHA: ${baseSha}`,
          ].join("\n"),
        });
        if (!Number.isSafeInteger(releasePull.number) || releasePull.number < 1) {
          throw new Error("GitHub returned an invalid release PR number");
        }
        batch.releasePrNumber = releasePull.number;
        batch.releasePrUrl = releasePull.url;
        batch.status = "TESTING";
        batch.test.dispatchRequestedAt = this.timestamp();
        batch.updatedAt = this.timestamp();
        await this.save();

        await this.github.dispatchWorkflow(this.config.testWorkflow, this.config.mainBranch, {
          candidate_ref: releaseBranch,
          candidate_sha: batch.releaseSha,
          release_batch_id: batch.id,
        });
        batch.test.dispatchSentAt = this.timestamp();
        batch.updatedAt = this.timestamp();
        await this.save();
        this.scheduleMonitor(0);
        return { batch: publicBatch(batch), replayed: false };
      } catch (error) {
        batch.status = "TEST_FAILED";
        batch.failureCode = publicFailureCode(error, "BATCH_PREPARATION_FAILED");
        batch.updatedAt = this.timestamp();
        await this.save();
        throw new PublicError(502, "BATCH_PREPARATION_FAILED", "发布批次准备失败，请查看批次状态");
      }
    });
  }

  async verifyPullRequest(number, baseSha) {
    const pullRequest = await this.github.getPullRequest(number);
    if (
      pullRequest?.state !== "open"
      || pullRequest?.draft === true
      || pullRequest?.base?.ref !== this.config.mainBranch
      || pullRequest?.mergeable !== true
      || pullRequest?.mergeable_state !== "clean"
    ) {
      throw new PublicError(409, "PR_NOT_READY", `PR #${number} 不符合发布条件`);
    }
    const headSha = validateSha(pullRequest?.head?.sha, `PR #${number} head SHA`);
    const files = await this.github.listPullRequestFiles(number);
    const verifiedPullRequest = await this.github.getPullRequest(number);
    if (verifiedPullRequest?.head?.sha !== headSha || files.length !== Number(pullRequest.changed_files || 0)) {
      throw new PublicError(409, "PR_CHANGED_DURING_VERIFICATION", `PR #${number} 在校验期间发生变化`);
    }
    const protectedFiles = protectedReleaseFiles(files);
    if (protectedFiles.length > 0) {
      throw new PublicError(409, "PR_PROTECTED_PATHS", `PR #${number} 修改了受保护的发布基础设施`);
    }
    const status = await this.github.getHeadStatus(headSha, testStatusContext(baseSha));
    if (status?.state !== "success" || status?.creator?.login !== TEST_STATUS_CREATOR) {
      throw new PublicError(409, "PR_TEST_NOT_SUCCESSFUL", `PR #${number} 构建检查未成功`);
    }
    return {
      number,
      headSha,
      title: String(pullRequest.title || "").slice(0, 200),
      modules: inferReleaseModules(files),
      url: pullRequest.html_url,
      statusId: status.id,
      statusTargetUrl: status.target_url || null,
      ...publicFileList(files),
    };
  }

  async matchingTestDeployment(batch) {
    try {
      const deployment = parseTestDeploymentMarker(await this.readTestBuildRef(), this.config);
      return deployment.buildRef === batch.releaseSha ? deployment : null;
    } catch (error) {
      this.logger.error("Test environment marker validation failed", error?.message || error?.name || "unknown error");
      return null;
    }
  }

  async markTestEnvironmentReplaced(batch) {
    batch.status = "TEST_FAILED";
    batch.failureCode = "TEST_ENVIRONMENT_REPLACED";
    batch.updatedAt = this.timestamp();
    await this.save();
  }

  async promoteBatch(id, rawInput) {
    validateBatchId(id);
    const input = validatePromoteInput(rawInput);
    return this.serialized(async () => {
      this.ensureConfigured();
      const hash = requestHash({ id, ...input });
      const key = `promote:${id}:${input.idempotencyKey}`;
      const previous = this.state.idempotency[key];
      if (previous) {
        if (previous.requestHash !== hash) {
          throw new PublicError(409, "IDEMPOTENCY_CONFLICT", "幂等键已用于不同请求");
        }
        return { batch: publicBatch(this.getBatch(previous.batchId)), replayed: true };
      }

      const batch = this.getBatch(id);
      if (batch.status !== "READY") {
        throw new PublicError(409, "BATCH_NOT_READY", "发布批次尚未通过测试验收");
      }
      const testDeployment = await this.matchingTestDeployment(batch);
      if (
        !testDeployment
        || testDeployment.backendImage !== batch.backendImage
        || testDeployment.frontendImage !== batch.frontendImage
      ) {
        await this.markTestEnvironmentReplaced(batch);
        throw new PublicError(409, "TEST_ENVIRONMENT_REPLACED", "测试环境当前版本已变化，请重新创建发布批次");
      }
      const currentMainSha = validateSha(await this.github.getBranchSha(this.config.mainBranch), "main SHA");
      if (currentMainSha !== batch.baseSha) {
        batch.status = "STALE";
        batch.failureCode = "MAIN_BRANCH_CHANGED";
        batch.updatedAt = this.timestamp();
        await this.save();
        throw new PublicError(409, "MAIN_BRANCH_CHANGED", "main 分支已变更，请重新创建发布批次");
      }
      this.verifiedTestRunId(batch);

      this.state.idempotency[key] = { batchId: id, requestHash: hash, operation: "promote" };
      batch.status = "PROMOTING";
      batch.productionSha = batch.releaseSha;
      batch.promotedBy = copy(input.requestedBy);
      batch.promoteRequestedAt = this.timestamp();
      batch.failureCode = null;
      batch.updatedAt = this.timestamp();
      await this.save();

      try {
        await this.fastForwardMain(batch);
        await this.github.closePullRequest(batch.releasePrNumber);
        await this.dispatchProduction(batch);
        this.scheduleMonitor(0);
        return { batch: publicBatch(batch), replayed: false };
      } catch (error) {
        batch.status = error instanceof PublicError && error.code === "MAIN_BRANCH_CHANGED"
          ? "STALE"
          : "PROD_FAILED";
        batch.failureCode = publicFailureCode(error, "PRODUCTION_PROMOTION_FAILED");
        batch.updatedAt = this.timestamp();
        await this.save();
        if (error instanceof PublicError) {
          throw error;
        }
        throw new PublicError(502, "PRODUCTION_PROMOTION_FAILED", "生产发布启动失败，请查看批次状态");
      }
    });
  }

  async fastForwardMain(batch) {
    try {
      await this.github.updateBranchRefFastForward(this.config.mainBranch, batch.productionSha);
    } catch (error) {
      const observedMain = validateSha(await this.github.getBranchSha(this.config.mainBranch), "main SHA after failed fast-forward");
      if (observedMain !== batch.baseSha && observedMain !== batch.productionSha) {
        throw new PublicError(409, "MAIN_BRANCH_CHANGED", "main 分支在提升时发生变更");
      }
      throw error;
    }
    const observedMain = validateSha(await this.github.getBranchSha(this.config.mainBranch), "main SHA after fast-forward");
    if (observedMain !== batch.productionSha) {
      throw new PublicError(409, "MAIN_BRANCH_RACE", "main 分支未停留在已测试的精确提交");
    }
  }

  verifiedTestRunId(batch) {
    const testRunId = batch.test?.runId;
    if (!Number.isSafeInteger(testRunId) || testRunId < 1) {
      throw new PublicError(409, "TEST_RUN_UNAVAILABLE", "测试验收记录不完整，请重新创建发布批次");
    }
    return testRunId;
  }

  productionSignature(batch, testRunId) {
    return createHmac("sha256", this.config.productionHmacSecret)
      .update(`${batch.id}:${batch.productionSha}:${batch.backendImage}:${batch.frontendImage}:${testRunId}`)
      .digest("hex");
  }

  async dispatchProduction(batch) {
    const testRunId = this.verifiedTestRunId(batch);
    const releaseSignature = this.productionSignature(batch, testRunId);
    batch.production.dispatchRequestedAt = this.timestamp();
    batch.updatedAt = this.timestamp();
    await this.save();
    await this.github.dispatchWorkflow(this.config.prodWorkflow, this.config.mainBranch, {
      confirm: "DEPLOY_PRODUCTION",
      release_sha: batch.productionSha,
      release_batch_id: batch.id,
      test_run_id: String(testRunId),
      backend_image: batch.backendImage,
      frontend_image: batch.frontendImage,
      release_signature: releaseSignature,
    });
    batch.production.dispatchSentAt = this.timestamp();
    batch.updatedAt = this.timestamp();
    await this.save();
  }

  async retryBatch(id, rawInput) {
    validateBatchId(id);
    const input = validatePromoteInput(rawInput);
    return this.serialized(async () => {
      this.ensureConfigured();
      const hash = requestHash({ id, ...input });
      const key = `retry:${id}:${input.idempotencyKey}`;
      const previous = this.state.idempotency[key];
      if (previous) {
        if (previous.requestHash !== hash) {
          throw new PublicError(409, "IDEMPOTENCY_CONFLICT", "幂等键已用于不同请求");
        }
        return { batch: publicBatch(this.getBatch(previous.batchId)), replayed: true };
      }

      const batch = this.getBatch(id);
      if (batch.status !== "PROD_FAILED") {
        throw new PublicError(409, "BATCH_NOT_RETRYABLE", "只有生产发布失败的批次可以重试");
      }
      if (batch.productionSha !== batch.releaseSha) {
        throw new PublicError(409, "PRODUCTION_RETRY_UNSAFE", "批次生产提交与已测试提交不一致，禁止重试");
      }
      const currentMainSha = validateSha(await this.github.getBranchSha(this.config.mainBranch), "main SHA");
      if (currentMainSha !== batch.baseSha && currentMainSha !== batch.productionSha) {
        throw new PublicError(409, "PRODUCTION_RETRY_UNSAFE", "main 已离开安全提交，禁止自动重试");
      }
      this.verifiedTestRunId(batch);

      this.state.idempotency[key] = { batchId: id, requestHash: hash, operation: "retry" };
      batch.status = "PROMOTING";
      batch.retriedBy = copy(input.requestedBy);
      batch.retryRequestedAt = this.timestamp();
      batch.failureCode = null;
      batch.updatedAt = this.timestamp();
      await this.save();

      try {
        if (currentMainSha === batch.baseSha) {
          await this.fastForwardMain(batch);
        }
        await this.github.closePullRequest(batch.releasePrNumber);

        const existingRun = await this.findWorkflowRun(batch, "production");
        if (existingRun && (existingRun.status !== "completed" || existingRun.conclusion === "success")) {
          await this.applyWorkflowRun(batch, "production", existingRun);
          await this.save();
          this.scheduleMonitor(0);
          return { batch: publicBatch(batch), replayed: false };
        }

        await this.dispatchProduction(batch);
        this.scheduleMonitor(0);
        return { batch: publicBatch(batch), replayed: false };
      } catch (error) {
        batch.status = "PROD_FAILED";
        batch.failureCode = publicFailureCode(error, "PRODUCTION_PROMOTION_FAILED");
        batch.updatedAt = this.timestamp();
        await this.save();
        if (error instanceof PublicError) {
          throw error;
        }
        throw new PublicError(502, "PRODUCTION_RETRY_FAILED", "生产发布重试失败，请查看批次状态");
      }
    });
  }

  testRunDisplayTitle(batch) {
    return `Release test ${batch.id} ${batch.releaseSha}`;
  }

  productionRunDisplayTitle(batch) {
    return `Production ${batch.id} ${batch.productionSha}`;
  }

  scheduleMonitor(delay = this.pollIntervalMs) {
    if (
      !this.autoPoll
      || !this.github
      || this.monitorTimer
      || !this.state?.batches.some((batch) => ["TESTING", "PROMOTING"].includes(batch.status))
    ) {
      return;
    }
    this.monitorTimer = setTimeout(async () => {
      this.monitorTimer = null;
      try {
        await this.pollOnce();
      } catch (error) {
        this.logger.error("Release workflow polling failed", error?.message || error?.name || "unknown error");
      } finally {
        this.scheduleMonitor();
      }
    }, delay);
    this.monitorTimer.unref?.();
  }

  close() {
    if (this.monitorTimer) {
      clearTimeout(this.monitorTimer);
      this.monitorTimer = null;
    }
  }

  async pollOnce() {
    return this.serialized(async () => {
      let changed = false;
      for (const batch of this.state.batches) {
        if (batch.status === "TESTING") {
          changed = await this.pollWorkflow(batch, "test") || changed;
        } else if (batch.status === "PROMOTING") {
          changed = await this.pollWorkflow(batch, "production") || changed;
        }
      }
      if (changed) {
        await this.save();
      }
    });
  }

  async findWorkflowRun(batch, phase) {
    const production = phase === "production";
    const workflow = production ? this.config.prodWorkflow : this.config.testWorkflow;
    const branch = this.config.mainBranch;
    const expectedSha = production ? batch.productionSha : batch.releaseSha;
    const details = production ? batch.production : batch.test;
    const expectedDisplayTitle = production
      ? this.productionRunDisplayTitle(batch)
      : this.testRunDisplayTitle(batch);
    const runs = await this.github.listWorkflowRuns(workflow, branch, expectedDisplayTitle);
    const threshold = Date.parse(details.dispatchRequestedAt) - 10_000;
    return runs
      .filter((candidate) => candidate.event === "workflow_dispatch")
      .filter((candidate) => candidate.head_branch === branch)
      .filter((candidate) => candidate.display_title === expectedDisplayTitle)
      .filter((candidate) => !production || candidate.head_sha === expectedSha)
      .filter((candidate) => Date.parse(candidate.created_at) >= threshold)
      .sort((left, right) => Number(right.id) - Number(left.id))[0] || null;
  }

  async applyWorkflowRun(batch, phase, run) {
    const production = phase === "production";
    const details = production ? batch.production : batch.test;
    details.runId = run.id;
    details.runUrl = run.html_url;
    details.status = run.status;
    details.conclusion = run.conclusion || null;
    if (run.status === "completed") {
      if (run.conclusion === "success") {
        if (!production) {
          const deployment = await this.matchingTestDeployment(batch);
          if (!deployment) {
            batch.status = "TEST_FAILED";
            batch.failureCode = "TEST_ENVIRONMENT_REPLACED";
          } else {
            batch.backendImage = deployment.backendImage;
            batch.frontendImage = deployment.frontendImage;
            batch.status = "READY";
            batch.failureCode = null;
            batch.readyAt = this.timestamp();
          }
        } else {
          batch.status = "RELEASED";
          batch.failureCode = null;
          batch.releasedAt = this.timestamp();
        }
      } else {
        batch.status = production ? "PROD_FAILED" : "TEST_FAILED";
        batch.failureCode = production ? "PRODUCTION_WORKFLOW_FAILED" : "TEST_WORKFLOW_FAILED";
      }
    }
    batch.updatedAt = this.timestamp();
  }

  async pollWorkflow(batch, phase) {
    const production = phase === "production";
    const details = production ? batch.production : batch.test;
    const run = await this.findWorkflowRun(batch, phase);
    details.lastPolledAt = this.timestamp();
    if (!run) {
      const elapsed = this.now().getTime() - Date.parse(details.dispatchRequestedAt);
      if (elapsed >= this.workflowRunTimeoutMs) {
        batch.status = production ? "PROD_FAILED" : "TEST_FAILED";
        batch.failureCode = production
          ? "PRODUCTION_WORKFLOW_NOT_FOUND_TIMEOUT"
          : "TEST_WORKFLOW_NOT_FOUND_TIMEOUT";
        details.status = "not_found";
        details.conclusion = "timed_out";
        batch.updatedAt = this.timestamp();
      }
      return true;
    }
    await this.applyWorkflowRun(batch, phase, run);
    return true;
  }
}
