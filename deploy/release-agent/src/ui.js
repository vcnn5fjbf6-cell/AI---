const state = {
  context: null,
  batches: [],
  selected: new Set(),
  moduleFilter: "all",
  busy: false,
};

const statusText = {
  PREPARING: "准备批次",
  TESTING: "组合测试中",
  READY: "等待上线确认",
  TEST_FAILED: "测试失败",
  STALE: "基线已失效",
  PROMOTING: "生产发布中",
  PROD_FAILED: "生产发布失败",
  RELEASED: "已上线",
};

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function idempotencyKey(prefix) {
  const value = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  return `${prefix}:${value}`;
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: options.body ? { "Content-Type": "application/json" } : undefined,
    cache: "no-store",
  });
  const payload = await response.json();
  if (!response.ok) {
    throw new Error(payload?.error?.message || `请求失败（${response.status}）`);
  }
  return payload;
}

function showNotice(message, error = false) {
  const notice = document.querySelector("#notice");
  notice.textContent = message || "";
  notice.className = message ? `notice${error ? " error" : ""}` : "notice hidden";
}

function statusBadge(status) {
  const failed = status === "TEST_FAILED" || status === "PROD_FAILED" || status === "STALE";
  const pending = status === "PREPARING" || status === "TESTING" || status === "PROMOTING";
  return element("span", `badge${failed ? " fail" : pending ? " warn" : ""}`, statusText[status] || status);
}

function candidateModules(candidate) {
  return candidate.modules?.length ? candidate.modules : [{ code: "shared", label: "通用基础" }];
}

function collectModules(items) {
  const modules = new Map();
  for (const item of items) {
    for (const module of candidateModules(item)) {
      modules.set(module.code, module);
    }
  }
  return [...modules.values()];
}

function moduleTags(modules) {
  const list = element("div", "module-tags");
  for (const module of modules) {
    list.append(element("span", "module-tag", module.label));
  }
  return list;
}

function filteredCandidates(candidates) {
  if (state.moduleFilter === "all") return candidates;
  return candidates.filter((candidate) => candidateModules(candidate).some((module) => module.code === state.moduleFilter));
}

function renderModuleFilters(candidates) {
  const container = document.querySelector("#module-filter-list");
  container.replaceChildren();
  const summaries = new Map();
  for (const candidate of candidates) {
    for (const module of candidateModules(candidate)) {
      const current = summaries.get(module.code) || { ...module, total: 0, ready: 0 };
      current.total += 1;
      if (candidate.ready) current.ready += 1;
      summaries.set(module.code, current);
    }
  }

  const buttons = [{ code: "all", label: "全部模块", total: candidates.length, ready: candidates.filter((item) => item.ready).length }, ...summaries.values()];
  for (const item of buttons) {
    const button = element("button", `module-filter${state.moduleFilter === item.code ? " active" : ""}`, `${item.label} ${item.ready}/${item.total}`);
    button.type = "button";
    button.addEventListener("click", () => {
      state.moduleFilter = item.code;
      renderCandidates();
    });
    container.append(button);
  }
}

function batchCard(batch, active = false) {
  const card = element("article", "batch-card");
  const top = element("div", "batch-top");
  const content = element("div");
  content.append(
    element("div", "batch-title", `批次 ${batch.id}`),
    element("div", "batch-meta", `包含 PR：${batch.prNumbers.map((number) => `#${number}`).join("、")} · 创建于 ${new Date(batch.createdAt).toLocaleString("zh-CN")}`),
  );
  top.append(content, statusBadge(batch.status));
  card.append(top);
  if (batch.modules?.length) {
    card.append(moduleTags(batch.modules));
  }

  if (batch.releaseSha) {
    card.append(element("p", "sha", `已测试提交：${batch.releaseSha}`));
  }
  if (batch.failureReason) {
    card.append(element("p", "candidate-description", `失败原因：${batch.failureReason}`));
  }
  const links = element("div", "batch-meta");
  if (batch.testUrl) {
    const link = element("a", null, "查看组合测试");
    link.href = batch.testUrl;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    links.append(link);
  }
  if (batch.productionUrl) {
    if (links.childNodes.length) links.append(" · ");
    const link = element("a", null, "查看生产发布");
    link.href = batch.productionUrl;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    links.append(link);
  }
  if (links.childNodes.length) card.append(links);

  if (active && (batch.status === "READY" || batch.status === "PROD_FAILED")) {
    const actions = element("div", "batch-actions");
    const button = element(
      "button",
      `button ${batch.status === "READY" ? "danger" : "secondary"}`,
      batch.status === "READY" ? "确认上线到生产" : "安全重试生产发布",
    );
    button.type = "button";
    button.disabled = state.busy;
    button.addEventListener("click", () => batch.status === "READY" ? promote(batch.id) : retry(batch.id));
    actions.append(button);
    card.append(actions);
  }
  return card;
}

function renderActive() {
  const container = document.querySelector("#active-batch");
  container.replaceChildren();
  const active = state.context?.activeBatch;
  if (!active) {
    container.className = "empty";
    container.textContent = "当前没有进行中的上线批次。";
    return;
  }
  container.className = "";
  container.append(batchCard(active, true));
}

function renderCandidates() {
  const container = document.querySelector("#candidate-list");
  container.replaceChildren();
  const candidates = state.context?.candidates || [];
  const visibleCandidates = filteredCandidates(candidates);
  const readyCount = candidates.filter((item) => item.ready).length;
  const blockedByBatch = Boolean(state.context?.activeBatch);
  const availableNumbers = new Set(candidates.filter((item) => item.ready).map((item) => item.number));
  state.selected = new Set([...state.selected].filter((number) => availableNumbers.has(number)));
  renderModuleFilters(candidates);
  document.querySelector("#candidate-summary").textContent = candidates.length
    ? `共 ${candidates.length} 项更新，其中 ${readyCount} 项已通过检查`
    : "暂无候选更新";

  if (!candidates.length) {
    container.append(element("div", "empty", state.context?.configured ? "暂无可读取的开放 PR。" : "配置仓库专属 GitHub App 后会显示可上线更新。"));
  }
  if (candidates.length && !visibleCandidates.length) {
    container.append(element("div", "empty", "当前模块暂无可上线更新。"));
  }
  for (const candidate of visibleCandidates) {
    const selectable = candidate.ready && !blockedByBatch && !state.busy;
    const row = element("article", `candidate${candidate.ready ? "" : " unavailable"}${state.selected.has(candidate.number) ? " selected" : ""}`);
    const selector = element("label", "candidate-selector");
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.disabled = !selectable;
    checkbox.checked = state.selected.has(candidate.number);
    checkbox.setAttribute("aria-label", `选择 PR #${candidate.number}`);
    checkbox.addEventListener("change", () => {
      if (checkbox.checked) state.selected.add(candidate.number);
      else state.selected.delete(candidate.number);
      row.classList.toggle("selected", checkbox.checked);
      selector.querySelector("span").textContent = checkbox.checked ? "已选择" : "选择";
      updateCreateButton();
    });
    selector.append(checkbox, element("span", null, checkbox.checked ? "已选择" : "选择"));
    const info = element("div");
    info.append(
      element("div", "candidate-title", `#${candidate.number} ${candidate.title}`),
      moduleTags(candidateModules(candidate)),
      element("div", "candidate-meta", `${candidate.author} · ${candidate.headBranch} · ${candidate.changedFiles} 个文件 · +${candidate.additions} / -${candidate.deletions}`),
    );
    if (candidate.body) info.append(element("p", "candidate-description", candidate.body));
    if (candidate.files?.length) {
      const suffix = candidate.fileListTruncated ? " 等" : "";
      info.append(element("p", "candidate-files", `变更文件：${candidate.files.join("、")}${suffix}`));
    }
    row.append(
      selector,
      info,
      element("span", `badge${candidate.ready ? "" : " warn"}`, candidate.ready ? "测试已通过" : candidate.blockedReason || "暂不可上线"),
    );
    container.append(row);
  }
  updateCreateButton();
}

function renderHistory() {
  const container = document.querySelector("#batch-history");
  container.replaceChildren();
  if (!state.batches.length) {
    container.append(element("div", "empty", "还没有上线批次记录。"));
    return;
  }
  for (const batch of state.batches) container.append(batchCard(batch));
}

function updateCreateButton() {
  const button = document.querySelector("#create-button");
  const blockedByBatch = Boolean(state.context?.activeBatch);
  const configured = Boolean(state.context?.configured);
  button.disabled = state.busy || state.selected.size === 0 || blockedByBatch || !configured;
  button.textContent = state.busy ? "正在处理…" : "创建批次并开始组合测试";

  document.querySelector("#selected-count").textContent = `已选 ${state.selected.size} 项`;
  const help = document.querySelector("#selected-help");
  if (!configured) help.textContent = "请先完成上线控制台配置";
  else if (blockedByBatch) help.textContent = "当前已有进行中的批次，完成后可继续创建";
  else if (!state.selected.size) help.textContent = "请先勾选需要上线的更新";
  else help.textContent = "创建后会自动部署到测试环境进行组合验收";

  const selectVisibleButton = document.querySelector("#select-visible-button");
  const readyVisible = filteredCandidates(state.context?.candidates || []).filter((candidate) => candidate.ready);
  selectVisibleButton.disabled = state.busy || readyVisible.length === 0 || Boolean(state.context?.activeBatch) || !state.context?.configured;
  selectVisibleButton.textContent = readyVisible.length ? `全选当前筛选（${readyVisible.length}）` : "全选当前筛选";
  document.querySelector("#clear-selection-button").disabled = state.busy || state.selected.size === 0;
}

function render() {
  renderActive();
  renderCandidates();
  renderHistory();
  if (!state.context?.configured) {
    showNotice(state.context?.reason || "发布控制面尚未配置仓库专属 GitHub App。", true);
  }
}

async function refresh(silent = false) {
  try {
    const [context, history] = await Promise.all([api("/api/context"), api("/api/batches")]);
    state.context = context;
    state.batches = history.batches || [];
    if (!silent) showNotice("");
    render();
  } catch (error) {
    showNotice(error.message, true);
  }
}

async function mutate(action, successMessage) {
  state.busy = true;
  render();
  try {
    await action();
    state.selected.clear();
    await refresh(true);
    showNotice(successMessage);
  } catch (error) {
    showNotice(error.message, true);
  } finally {
    state.busy = false;
    render();
  }
}

async function createBatch() {
  const prNumbers = [...state.selected].sort((left, right) => left - right);
  const selectedCandidates = (state.context?.candidates || []).filter((candidate) => state.selected.has(candidate.number));
  const moduleLabels = collectModules(selectedCandidates).map((module) => module.label).join("、") || "未识别";
  if (!prNumbers.length || !confirm(`确认用 ${prNumbers.map((number) => `#${number}`).join("、")} 创建上线批次？\n涉及模块：${moduleLabels}`)) return;
  await mutate(
    () => api("/api/batches", {
      method: "POST",
      body: JSON.stringify({ prNumbers, idempotencyKey: idempotencyKey("create") }),
    }),
    "上线批次已创建，正在进行组合测试。",
  );
}

function selectVisibleCandidates() {
  for (const candidate of filteredCandidates(state.context?.candidates || [])) {
    if (candidate.ready) state.selected.add(candidate.number);
  }
  renderCandidates();
}

function clearSelection() {
  state.selected.clear();
  renderCandidates();
}

async function promote(id) {
  const confirmation = prompt("即将把已测试的精确提交发布到生产。请输入 DEPLOY_PRODUCTION 确认：", "");
  if (confirmation !== "DEPLOY_PRODUCTION") return;
  await mutate(
    () => api(`/api/batches/${encodeURIComponent(id)}/promote`, {
      method: "POST",
      body: JSON.stringify({ confirmation, idempotencyKey: idempotencyKey("promote") }),
    }),
    "生产发布已启动。",
  );
}

async function retry(id) {
  if (!confirm("只会在 main 仍处于安全提交时重试。确认继续？")) return;
  await mutate(
    () => api(`/api/batches/${encodeURIComponent(id)}/retry`, {
      method: "POST",
      body: JSON.stringify({
        confirmation: "DEPLOY_PRODUCTION",
        idempotencyKey: idempotencyKey("retry"),
      }),
    }),
    "已提交安全重试。",
  );
}

document.querySelector("#refresh-button").addEventListener("click", () => refresh());
document.querySelector("#create-button").addEventListener("click", createBatch);
document.querySelector("#select-visible-button").addEventListener("click", selectVisibleCandidates);
document.querySelector("#clear-selection-button").addEventListener("click", clearSelection);
refresh();
setInterval(() => refresh(true), 10_000);
