<template>
  <section class="developer-center">
    <header class="developer-toolbar">
      <div class="toolbar-context">
        <span class="toolbar-context-icon">
          <el-icon><Reading /></el-icon>
        </span>
        <div>
          <strong>开发文档</strong>
          <small>随测试环境版本发布</small>
        </div>
      </div>

      <div ref="searchContainerRef" class="developer-search">
        <el-input
          ref="searchInputRef"
          v-model="searchQuery"
          :prefix-icon="Search"
          size="large"
          clearable
          role="combobox"
          aria-label="搜索开发文档、API、权限码或错误码"
          aria-autocomplete="list"
          :aria-expanded="searchOpen"
          aria-controls="developer-search-results"
          :aria-activedescendant="
            searchOpen && searchResults[selectedSearchIndex]
              ? `developer-search-result-${searchResults[selectedSearchIndex].key}`
              : undefined
          "
          placeholder="搜索文档、API、权限码、错误码"
          @focus="openSearch"
          @keydown.down.prevent="moveSearchSelection(1)"
          @keydown.up.prevent="moveSearchSelection(-1)"
          @keydown.enter.prevent="activateSearchSelection"
          @keydown.esc="closeSearch"
        >
          <template #suffix>
            <kbd class="search-shortcut">Ctrl K</kbd>
          </template>
        </el-input>

        <div
          v-if="searchOpen"
          id="developer-search-results"
          class="search-results"
          role="listbox"
          aria-label="开发文档搜索结果"
        >
          <div class="search-results-head">
            <span>{{ searchQuery.trim() ? "搜索结果" : "推荐入口" }}</span>
            <small>{{ searchResults.length }} 项</small>
          </div>
          <button
            v-for="(document, index) in searchResults"
            :id="`developer-search-result-${document.key}`"
            :key="document.key"
            type="button"
            class="search-result"
            :class="{ 'is-selected': selectedSearchIndex === index }"
            role="option"
            :aria-selected="selectedSearchIndex === index"
            @mouseenter="selectedSearchIndex = index"
            @click="selectDocument(document.key)"
          >
            <span class="search-result-icon">
              <el-icon><component :is="documentIcon(document.key)" /></el-icon>
            </span>
            <span>
              <strong>{{ document.title }}</strong>
              <small>{{ document.group }} · {{ document.summary }}</small>
            </span>
            <el-icon><ArrowRight /></el-icon>
          </button>
          <div v-if="searchResults.length === 0" class="search-empty">
            <el-icon><Search /></el-icon>
            <strong>没有找到相关内容</strong>
            <span>试试 API 路径、appCode、权限码或错误码。</span>
          </div>
        </div>
      </div>

      <div class="toolbar-actions">
        <div class="environment-switch" aria-label="文档环境">
          <span class="environment-option is-active">
            <span class="environment-dot" aria-hidden="true"></span>
            测试环境
          </span>
          <el-tooltip content="生产环境不开放开发者中心和在线调试" placement="bottom">
            <span class="environment-option is-disabled" aria-disabled="true">生产不可用</span>
          </el-tooltip>
        </div>
        <el-button :icon="Connection" @click="selectDocument('api-testing')">API 调试</el-button>
        <el-button type="primary" :icon="MagicStick" @click="selectDocument('ai-context')">给 AI</el-button>
      </div>
    </header>

    <div class="developer-mobile-nav">
      <el-select
        :model-value="activeDocumentKey"
        aria-label="选择开发文档"
        @change="(value: string) => selectDocument(value)"
      >
        <el-option-group
          v-for="group in developerDocumentGroups"
          :key="group.label"
          :label="group.label"
        >
          <el-option
            v-for="documentKey in group.documents"
            :key="documentKey"
            :label="documentByKey(documentKey).title"
            :value="documentKey"
          />
        </el-option-group>
      </el-select>
    </div>

    <div class="developer-workspace">
      <aside class="docs-navigation" aria-label="开发文档目录">
        <div class="docs-navigation-head">
          <div>
            <strong>开发文档</strong>
            <small>{{ developerDocuments.length }} 个主题</small>
          </div>
          <el-tooltip content="打开模块开发指南 Markdown" placement="right">
            <a
              class="navigation-head-action"
              href="/developer/module-development-guide.md"
              target="_blank"
              rel="noopener noreferrer"
              aria-label="打开模块开发指南 Markdown（新窗口）"
            >
              <el-icon><Document /></el-icon>
            </a>
          </el-tooltip>
        </div>

        <nav>
          <section v-for="group in developerDocumentGroups" :key="group.label" class="docs-group">
            <h2>{{ group.label }}</h2>
            <button
              v-for="documentKey in group.documents"
              :key="documentKey"
              type="button"
              class="docs-link"
              :class="{ 'is-active': activeDocumentKey === documentKey }"
              @click="selectDocument(documentKey)"
            >
              <el-icon><component :is="documentIcon(documentKey)" /></el-icon>
              <span>{{ documentByKey(documentKey).title }}</span>
            </button>
          </section>
        </nav>

        <div class="docs-navigation-foot">
          <span>当前文档版本</span>
          <strong>2026.07</strong>
        </div>
      </aside>

      <article
        ref="articleRef"
        class="document-article"
        tabindex="-1"
        :aria-labelledby="`document-title-${activeDocument.key}`"
      >
        <header class="document-head">
          <div class="document-breadcrumb">
            <span>开发者中心</span>
            <el-icon><ArrowRight /></el-icon>
            <span>{{ activeDocument.group }}</span>
          </div>
          <div class="document-title-row">
            <div>
              <h1 :id="`document-title-${activeDocument.key}`">{{ activeDocument.title }}</h1>
              <p>{{ activeDocument.summary }}</p>
            </div>
            <div class="document-actions">
              <el-button :icon="CopyDocument" @click="copyDocumentLink">复制页面</el-button>
              <el-button
                :icon="Document"
                tag="a"
                href="/developer/module-development-guide.md"
                target="_blank"
                rel="noopener noreferrer"
              >
                开发指南
              </el-button>
            </div>
          </div>
          <div class="document-meta">
            <el-tag type="success" effect="plain">测试环境可用</el-tag>
            <span>更新于 {{ activeDocument.updatedAt }}</span>
            <span>·</span>
            <span>内部开发者文档</span>
          </div>
        </header>

        <section
          v-for="section in activeDocument.sections"
          :id="section.id"
          :key="section.id"
          class="document-section"
          tabindex="-1"
        >
          <h2>{{ section.title }}</h2>

          <p v-for="paragraph in section.paragraphs" :key="paragraph">
            {{ paragraph }}
          </p>

          <el-alert
            v-if="section.alert"
            class="document-alert"
            :type="section.alert.type"
            :closable="false"
            show-icon
            :title="section.alert.title"
            :description="section.alert.description"
          />

          <ul v-if="section.bullets" class="document-list">
            <li v-for="item in section.bullets" :key="item">{{ item }}</li>
          </ul>

          <ol v-if="section.steps" class="document-steps">
            <li v-for="(step, index) in section.steps" :key="step.title">
              <span class="document-step-index">{{ index + 1 }}</span>
              <div>
                <div class="document-step-title">
                  <strong>{{ step.title }}</strong>
                  <code v-if="step.meta">{{ step.meta }}</code>
                </div>
                <p>{{ step.description }}</p>
              </div>
            </li>
          </ol>

          <div v-if="section.code" class="code-block">
            <div class="code-block-head">
              <span>{{ section.code.label }}</span>
              <div>
                <el-tag size="small" type="info" effect="plain">{{ section.code.language }}</el-tag>
                <el-button
                  link
                  type="primary"
                  :icon="CopyDocument"
                  @click="copyText(section.code.value)"
                >
                  复制
                </el-button>
              </div>
            </div>
            <pre><code>{{ section.code.value }}</code></pre>
          </div>

          <div v-if="section.endpoints" class="endpoint-table">
            <div class="endpoint-head" aria-hidden="true">
              <span>方法与路径</span>
              <span>用途</span>
              <span>权限</span>
              <span>风险</span>
            </div>
            <article v-for="endpoint in section.endpoints" :key="`${endpoint.method}-${endpoint.path}`">
              <div class="endpoint-route">
                <el-tag
                  size="small"
                  :type="endpointTagType(endpoint.method)"
                  :effect="endpoint.method === 'GET' ? 'light' : 'plain'"
                >
                  {{ endpoint.method }}
                </el-tag>
                <code>{{ endpoint.path }}</code>
              </div>
              <span class="endpoint-summary">{{ endpoint.summary }}</span>
              <span class="endpoint-permission">{{ endpoint.permission }}</span>
              <el-tag
                size="small"
                :type="endpoint.risk === '只读' ? 'success' : 'warning'"
                effect="plain"
              >
                {{ endpoint.risk }}
              </el-tag>
            </article>
          </div>

          <div v-if="section.links" class="resource-links">
            <article v-for="resource in section.links" :key="resource.path">
              <a
                :href="resource.path"
                target="_blank"
                rel="noopener noreferrer"
                :aria-label="`${resource.label}（新窗口）`"
              >
                <span class="resource-link-icon">
                  <el-icon><Document /></el-icon>
                </span>
                <span class="resource-link-copy">
                  <strong>{{ resource.label }}</strong>
                  <small>{{ resource.description }}</small>
                  <code>{{ resource.path }}</code>
                </span>
              </a>
              <div class="resource-link-actions">
                <el-tag size="small" type="info" effect="plain">{{ resource.format }}</el-tag>
                <el-button
                  :icon="CopyDocument"
                  circle
                  :aria-label="`复制 ${resource.label} 地址`"
                  @click="copyText(absoluteUrl(resource.path))"
                />
                <el-button
                  v-if="resource.downloadable !== false"
                  :icon="Download"
                  circle
                  :aria-label="`下载 ${resource.label}`"
                  @click="downloadResource(resource.path)"
                />
              </div>
            </article>
          </div>
        </section>

        <nav class="document-pager" aria-label="上一篇和下一篇">
          <button
            v-if="previousDocument"
            type="button"
            class="pager-link"
            @click="selectDocument(previousDocument.key)"
          >
            <small>上一篇</small>
            <span><el-icon><ArrowLeft /></el-icon>{{ previousDocument.title }}</span>
          </button>
          <span v-else></span>
          <button
            v-if="nextDocument"
            type="button"
            class="pager-link is-next"
            @click="selectDocument(nextDocument.key)"
          >
            <small>下一篇</small>
            <span>{{ nextDocument.title }}<el-icon><ArrowRight /></el-icon></span>
          </button>
        </nav>
      </article>

      <aside class="document-aside" aria-label="当前文档辅助导航">
        <div class="document-aside-sticky">
          <section class="aside-section">
            <h2>本页目录</h2>
            <nav>
              <a
                v-for="section in activeDocument.sections"
                :key="section.id"
                :href="`#${activeDocument.key}/${section.id}`"
                @click.prevent="scrollToDocumentSection(section.id)"
              >
                {{ section.title }}
              </a>
            </nav>
          </section>

          <section class="aside-section">
            <h2>开发环境</h2>
            <div class="aside-environment">
              <div>
                <span class="environment-dot" aria-hidden="true"></span>
                <strong>TEST</strong>
                <el-tag size="small" type="success" effect="plain">可用</el-tag>
              </div>
              <code :title="testBaseUrl">{{ testBaseUrl }}</code>
              <small>在线调试会真实读取或修改测试数据。</small>
            </div>
          </section>

          <section class="aside-section">
            <h2>常用资源</h2>
            <nav class="aside-resources">
              <a href="/swagger-ui/index.html" target="_blank" rel="noopener noreferrer">
                Swagger UI <el-icon><TopRight /></el-icon>
              </a>
              <a href="/v3/api-docs" target="_blank" rel="noopener noreferrer">
                OpenAPI JSON <el-icon><TopRight /></el-icon>
              </a>
              <a href="/llms.txt" target="_blank" rel="noopener noreferrer">
                llms.txt <el-icon><TopRight /></el-icon>
              </a>
              <a href="/developer/ai-context.md" target="_blank" rel="noopener noreferrer">
                AI Context <el-icon><TopRight /></el-icon>
              </a>
            </nav>
          </section>

          <el-button
            class="aside-ai-button"
            type="primary"
            plain
            :icon="MagicStick"
            @click="selectDocument('ai-context')"
          >
            交给 AI 开发
          </el-button>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { ElMessage, type InputInstance } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import {
  ArrowLeft,
  ArrowRight,
  Coin,
  Connection,
  CopyDocument,
  DataLine,
  Document,
  Download,
  Files,
  Guide,
  Key,
  MagicStick,
  Promotion,
  Reading,
  Search,
  Setting,
  Tickets,
  TopRight
} from "@element-plus/icons-vue";
import {
  developerDocumentGroups,
  developerDocuments,
  developerDocumentsByKey,
  type DeveloperDocument
} from "./developerCenterContent";

const defaultDocumentKey = "quickstart";
const route = useRoute();
const router = useRouter();
const activeDocumentKey = ref(defaultDocumentKey);
const searchQuery = ref("");
const searchOpen = ref(false);
const selectedSearchIndex = ref(0);
const searchContainerRef = ref<HTMLElement>();
const searchInputRef = ref<InputInstance>();
const articleRef = ref<HTMLElement>();
let locallyWrittenHash: string | null = null;

const activeDocument = computed(
  () => developerDocumentsByKey.get(activeDocumentKey.value) ?? developerDocuments[0]
);
const orderedDocuments = computed(() =>
  developerDocumentGroups.flatMap((group) =>
    group.documents.map((key) => documentByKey(key))
  )
);
const activeDocumentIndex = computed(() =>
  orderedDocuments.value.findIndex((document) => document.key === activeDocumentKey.value)
);
const previousDocument = computed(() =>
  activeDocumentIndex.value > 0 ? orderedDocuments.value[activeDocumentIndex.value - 1] : null
);
const nextDocument = computed(() =>
  activeDocumentIndex.value < orderedDocuments.value.length - 1
    ? orderedDocuments.value[activeDocumentIndex.value + 1]
    : null
);
const testBaseUrl = computed(() => window.location.origin);
const searchResults = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  const candidates = query
    ? developerDocuments.filter((document) => documentSearchText(document).includes(query))
    : ["quickstart", "api-testing", "access", "release", "ai-context"].map(documentByKey);
  return candidates.slice(0, 8);
});

function documentByKey(key: string): DeveloperDocument {
  return developerDocumentsByKey.get(key) ?? developerDocuments[0];
}

function documentSearchText(document: DeveloperDocument): string {
  return [
    document.title,
    document.summary,
    document.group,
    ...document.keywords,
    ...document.sections.flatMap((section) => [
      section.title,
      ...(section.paragraphs ?? []),
      ...(section.bullets ?? []),
      ...(section.steps?.flatMap((step) => [step.title, step.description, step.meta ?? ""]) ?? []),
      ...(section.code
        ? [section.code.label, section.code.language, section.code.value]
        : []),
      ...(section.endpoints?.flatMap((endpoint) => [
        endpoint.method,
        endpoint.path,
        endpoint.summary,
        endpoint.permission,
        endpoint.risk
      ]) ?? []),
      ...(section.links?.flatMap((link) => [link.label, link.path, link.description, link.format]) ?? []),
      ...(section.alert ? [section.alert.title, section.alert.description] : [])
    ])
  ]
    .join(" ")
    .toLowerCase();
}

function documentIcon(key: string) {
  if (key === "overview") return Guide;
  if (key === "quickstart") return Promotion;
  if (key === "manifest") return Tickets;
  if (key === "frontend") return Files;
  if (key === "backend") return Setting;
  if (key === "access") return Key;
  if (key === "migration") return Coin;
  if (key === "api-overview" || key === "api-testing") return Connection;
  if (key === "testing" || key === "release") return DataLine;
  if (key === "ai-context") return MagicStick;
  return Document;
}

function activeHashState(): { documentKey: string; sectionId?: string } {
  const [hashDocumentKey, hashSectionId] = route.hash.replace(/^#/, "").split("/");
  const documentKey = developerDocumentsByKey.has(hashDocumentKey)
    ? hashDocumentKey
    : defaultDocumentKey;
  const currentDocument = documentByKey(documentKey);
  const sectionId = currentDocument.sections.some((section) => section.id === hashSectionId)
    ? hashSectionId
    : undefined;
  return { documentKey, sectionId };
}

function syncDocumentFromHash(): void {
  const { documentKey, sectionId } = activeHashState();
  activeDocumentKey.value = documentKey;
  if (!sectionId) {
    return;
  }
  void nextTick(() => {
    const target = document.getElementById(sectionId);
    target?.scrollIntoView({ behavior: "auto", block: "start" });
    target?.focus({ preventScroll: true });
  });
}

function replaceRouteHash(hash: string): void {
  if (route.hash === hash) {
    return;
  }
  locallyWrittenHash = hash;
  void router.replace({
    path: route.path,
    query: route.query,
    hash
  }).catch(() => {
    locallyWrittenHash = null;
  });
}

function selectDocument(key: string): void {
  if (!developerDocumentsByKey.has(key)) {
    return;
  }
  activeDocumentKey.value = key;
  searchOpen.value = false;
  searchQuery.value = "";
  selectedSearchIndex.value = 0;
  replaceRouteHash(`#${key}`);
  void nextTick(() => {
    articleRef.value?.focus({ preventScroll: true });
    document.querySelector(".developer-toolbar")?.scrollIntoView({ block: "start" });
  });
}

function openSearch(): void {
  searchOpen.value = true;
  selectedSearchIndex.value = 0;
}

function closeSearch(): void {
  searchOpen.value = false;
}

function moveSearchSelection(offset: number): void {
  if (!searchOpen.value) {
    openSearch();
    return;
  }
  const count = searchResults.value.length;
  if (count === 0) return;
  selectedSearchIndex.value = (selectedSearchIndex.value + offset + count) % count;
}

function activateSearchSelection(): void {
  const document = searchResults.value[selectedSearchIndex.value];
  if (document) {
    selectDocument(document.key);
  }
}

function handleGlobalKeydown(event: KeyboardEvent): void {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
    event.preventDefault();
    searchInputRef.value?.focus();
    openSearch();
  }
}

function handleDocumentClick(event: MouseEvent): void {
  const target = event.target as Node | null;
  if (target && !searchContainerRef.value?.contains(target)) {
    closeSearch();
  }
}

function scrollToDocumentSection(sectionId: string): void {
  const target = document.getElementById(sectionId);
  if (!target) return;
  const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  target.scrollIntoView({ behavior: reduceMotion ? "auto" : "smooth", block: "start" });
  replaceRouteHash(`#${activeDocumentKey.value}/${sectionId}`);
  window.setTimeout(() => target.focus({ preventScroll: true }), reduceMotion ? 0 : 350);
}

function endpointTagType(method: string): "success" | "warning" | "danger" | "info" {
  if (method === "GET") return "success";
  if (method === "DELETE") return "danger";
  if (method === "UI") return "info";
  return "warning";
}

function absoluteUrl(path: string): string {
  return new URL(path, window.location.origin).toString();
}

function downloadResource(path: string): void {
  const link = document.createElement("a");
  link.href = path;
  link.download = path.split("/").pop() || "developer-resource";
  document.body.appendChild(link);
  link.click();
  link.remove();
}

async function copyText(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success("已复制");
  } catch {
    ElMessage.warning("复制失败，请手动选择内容");
  }
}

async function copyDocumentLink(): Promise<void> {
  await copyText(
    `${window.location.origin}${window.location.pathname}${window.location.search}#${activeDocumentKey.value}`
  );
}

watch(searchResults, () => {
  selectedSearchIndex.value = 0;
});

watch(
  () => route.hash,
  () => {
    if (route.hash === locallyWrittenHash) {
      locallyWrittenHash = null;
      return;
    }
    syncDocumentFromHash();
  }
);

onMounted(() => {
  syncDocumentFromHash();
  window.addEventListener("keydown", handleGlobalKeydown);
  document.addEventListener("click", handleDocumentClick);
});

onUnmounted(() => {
  window.removeEventListener("keydown", handleGlobalKeydown);
  document.removeEventListener("click", handleDocumentClick);
});
</script>

<style scoped>
.developer-center {
  min-width: 0;
}

.developer-toolbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: minmax(160px, 0.55fr) minmax(320px, 1.35fr) auto;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
}

.toolbar-context {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.toolbar-context-icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 7px;
  background: var(--app-primary-soft);
  color: var(--app-primary);
  font-size: 18px;
}

.toolbar-context div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.toolbar-context strong {
  color: var(--app-text);
  font-size: 14px;
}

.toolbar-context small {
  overflow: hidden;
  color: var(--app-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.developer-search {
  position: relative;
  min-width: 0;
}

.developer-search :deep(.el-input__wrapper) {
  border-radius: 7px;
  box-shadow: 0 0 0 1px var(--app-line) inset;
}

.developer-search :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--app-primary) inset, 0 0 0 3px rgba(22, 93, 255, 0.1);
}

.search-shortcut {
  padding: 2px 6px;
  border: 1px solid var(--app-line);
  border-radius: 4px;
  background: var(--app-surface-muted);
  color: var(--app-text-muted);
  font-family: inherit;
  font-size: 11px;
  line-height: 1.4;
}

.search-results {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  left: 0;
  z-index: 40;
  overflow: hidden;
  border: 1px solid var(--app-line);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.14);
}

.search-results-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--app-line-soft);
  background: var(--app-surface-muted);
  color: var(--app-text-muted);
  font-size: 12px;
}

.search-result {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-bottom: 1px solid var(--app-line-soft);
  background: #ffffff;
  color: var(--app-text);
  text-align: left;
  cursor: pointer;
}

.search-result:last-of-type {
  border-bottom: 0;
}

.search-result:hover,
.search-result.is-selected {
  background: #f2f6ff;
}

.search-result-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.search-result > span:nth-child(2) {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.search-result strong {
  font-size: 13px;
}

.search-result small {
  overflow: hidden;
  color: var(--app-text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-result > .el-icon {
  color: var(--app-text-muted);
}

.search-empty {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 28px 16px;
  color: var(--app-text-muted);
  text-align: center;
}

.search-empty > .el-icon {
  color: var(--app-primary);
  font-size: 24px;
}

.search-empty strong {
  color: var(--app-text);
  font-size: 14px;
}

.search-empty span {
  font-size: 12px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.environment-switch {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--app-line);
  border-radius: 7px;
  background: var(--app-surface-muted);
}

.environment-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 0 9px;
  border-radius: 5px;
  color: var(--app-text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.environment-option.is-active {
  background: #ffffff;
  color: var(--app-text);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.08);
  font-weight: 600;
}

.environment-option.is-disabled {
  color: #7c8ca1;
  cursor: not-allowed;
}

.environment-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--app-success);
  box-shadow: 0 0 0 3px rgba(16, 127, 75, 0.12);
}

.developer-mobile-nav {
  display: none;
  margin-top: 12px;
}

.developer-mobile-nav .el-select {
  width: 100%;
}

.developer-workspace {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr) 190px;
  align-items: stretch;
  min-height: calc(100vh - 210px);
  margin-top: 14px;
  border: 1px solid var(--app-line);
  border-radius: var(--app-radius);
  background: #ffffff;
  box-shadow: var(--app-shadow);
}

.docs-navigation {
  min-width: 0;
  border-right: 1px solid var(--app-line-soft);
  background: #fbfcfe;
}

.docs-navigation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 16px 14px 12px;
  border-bottom: 1px solid var(--app-line-soft);
}

.docs-navigation-head > div {
  display: grid;
  gap: 2px;
}

.docs-navigation-head strong {
  color: var(--app-text);
  font-size: 14px;
}

.docs-navigation-head small {
  color: var(--app-text-muted);
  font-size: 12px;
}

.navigation-head-action {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid var(--app-line);
  border-radius: 6px;
  background: #ffffff;
  color: var(--app-primary);
}

.navigation-head-action:hover,
.navigation-head-action:focus-visible {
  border-color: var(--app-primary);
  background: var(--app-primary-soft);
}

.docs-navigation nav {
  padding: 10px 8px 14px;
}

.docs-group + .docs-group {
  margin-top: 15px;
}

.docs-group h2 {
  margin: 0 8px 6px;
  color: #52647a;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.docs-link {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 36px;
  padding: 7px 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--app-text-muted);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.docs-link:hover,
.docs-link:focus-visible {
  background: #f0f4fa;
  color: var(--app-text);
}

.docs-link.is-active {
  background: var(--app-primary-soft);
  color: var(--app-primary);
  font-weight: 600;
}

.docs-link .el-icon {
  flex: 0 0 auto;
  font-size: 15px;
}

.docs-navigation-foot {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin: 6px 14px 16px;
  padding-top: 12px;
  border-top: 1px solid var(--app-line-soft);
  color: var(--app-text-muted);
  font-size: 11px;
}

.docs-navigation-foot strong {
  color: var(--app-text);
}

.document-article {
  min-width: 0;
  padding: 28px clamp(24px, 3vw, 46px) 34px;
  outline: none;
}

.document-head {
  padding-bottom: 24px;
  border-bottom: 1px solid var(--app-line-soft);
}

.document-breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 14px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.document-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.document-title-row > div:first-child {
  min-width: 0;
}

.document-title-row h1 {
  margin: 0;
  color: #172033;
  font-size: clamp(26px, 2.4vw, 34px);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.25;
}

.document-title-row p {
  max-width: 720px;
  margin: 10px 0 0;
  color: var(--app-text-muted);
  font-size: 15px;
  line-height: 1.75;
}

.document-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.document-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.document-section {
  max-width: 820px;
  padding-top: 30px;
  scroll-margin-top: 88px;
  outline: none;
}

.document-section h2 {
  margin: 0 0 14px;
  color: #172033;
  font-size: 21px;
  font-weight: 700;
  line-height: 1.35;
}

.document-section > p {
  margin: 0 0 12px;
  color: #475569;
  font-size: 14px;
  line-height: 1.85;
}

.document-alert {
  margin: 16px 0;
}

.document-alert :deep(.el-alert__description) {
  color: #52647a;
  font-size: 13px;
  line-height: 1.65;
}

.document-list {
  display: grid;
  gap: 9px;
  margin: 14px 0 0;
  padding-left: 22px;
  color: #475569;
  font-size: 14px;
  line-height: 1.75;
}

.document-list li::marker {
  color: var(--app-primary);
}

.document-steps {
  display: grid;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.document-steps li {
  position: relative;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 13px;
  padding-bottom: 20px;
}

.document-steps li:not(:last-child)::after {
  position: absolute;
  top: 31px;
  bottom: 0;
  left: 15px;
  width: 1px;
  background: #d9e4f1;
  content: "";
}

.document-step-index {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 31px;
  height: 31px;
  border: 1px solid #b9ccf8;
  border-radius: 7px;
  background: #f2f6ff;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
}

.document-step-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.document-step-title strong {
  color: var(--app-text);
  font-size: 14px;
}

.document-step-title code {
  color: #31527b;
  font-size: 11px;
}

.document-steps p {
  margin: 5px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.65;
}

.code-block {
  margin-top: 16px;
  overflow: hidden;
  border: 1px solid #26364d;
  border-radius: 7px;
  background: #111827;
}

.code-block-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 13px;
  border-bottom: 1px solid #293a52;
  color: #c5d2e4;
  font-size: 12px;
}

.code-block-head > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.code-block pre {
  max-height: 430px;
  margin: 0;
  padding: 16px;
  overflow: auto;
}

.code-block code {
  color: #dbe7f5;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", monospace;
  font-size: 12px;
  line-height: 1.7;
  tab-size: 2;
}

.endpoint-table {
  margin-top: 16px;
  overflow: hidden;
  border: 1px solid var(--app-line);
  border-radius: 7px;
}

.endpoint-head,
.endpoint-table article {
  display: grid;
  grid-template-columns: minmax(230px, 1.2fr) minmax(150px, 0.9fr) 120px 108px;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
}

.endpoint-head {
  background: var(--app-surface-muted);
  color: var(--app-text-muted);
  font-size: 11px;
  font-weight: 600;
}

.endpoint-table article {
  border-top: 1px solid var(--app-line-soft);
  color: var(--app-text-muted);
  font-size: 12px;
}

.endpoint-route {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.endpoint-route code {
  overflow-wrap: anywhere;
  color: #284f7c;
  font-size: 12px;
}

.endpoint-table article > .el-tag {
  justify-self: start;
}

.resource-links {
  display: grid;
  margin-top: 16px;
  overflow: hidden;
  border: 1px solid var(--app-line);
  border-radius: 7px;
}

.resource-links article {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 13px 14px;
  border-bottom: 1px solid var(--app-line-soft);
}

.resource-links article:last-child {
  border-bottom: 0;
}

.resource-links article:hover {
  background: #f8faff;
}

.resource-links article > a {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  align-items: center;
  gap: 11px;
  min-width: 0;
}

.resource-link-icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 6px;
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.resource-link-copy {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.resource-link-copy strong {
  color: var(--app-text);
  font-size: 13px;
}

.resource-link-copy small {
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.resource-link-copy code {
  overflow: hidden;
  color: #31527b;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resource-link-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.resource-link-actions .el-button {
  min-width: 36px;
  min-height: 36px;
}

.document-pager {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 42px;
  padding-top: 20px;
  border-top: 1px solid var(--app-line-soft);
}

.pager-link {
  display: grid;
  gap: 5px;
  min-height: 64px;
  padding: 11px 13px;
  border: 1px solid var(--app-line);
  border-radius: 7px;
  background: #ffffff;
  color: var(--app-text);
  text-align: left;
  cursor: pointer;
}

.pager-link:hover,
.pager-link:focus-visible {
  border-color: #a9c2fb;
  background: #f7f9ff;
}

.pager-link small {
  color: var(--app-text-muted);
  font-size: 11px;
}

.pager-link span {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 600;
}

.pager-link.is-next {
  text-align: right;
}

.pager-link.is-next span {
  justify-content: flex-end;
}

.document-aside {
  min-width: 0;
  border-left: 1px solid var(--app-line-soft);
  background: #fcfdff;
}

.document-aside-sticky {
  position: sticky;
  top: 84px;
  display: grid;
  gap: 20px;
  padding: 20px 16px;
}

.aside-section {
  min-width: 0;
}

.aside-section h2 {
  margin: 0 0 10px;
  color: #52647a;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.aside-section nav {
  display: grid;
  gap: 2px;
}

.aside-section nav a {
  padding: 6px 8px;
  border-left: 2px solid transparent;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.aside-section nav a:hover,
.aside-section nav a:focus-visible {
  border-left-color: var(--app-primary);
  background: var(--app-primary-soft);
  color: var(--app-primary);
}

.aside-environment {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--app-line);
  border-radius: 7px;
  background: #ffffff;
}

.aside-environment > div {
  display: flex;
  align-items: center;
  gap: 7px;
}

.aside-environment strong {
  font-size: 12px;
}

.aside-environment code {
  display: block;
  overflow: hidden;
  color: #31527b;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.aside-environment small {
  color: var(--app-text-muted);
  font-size: 11px;
  line-height: 1.55;
}

.aside-resources a {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.aside-ai-button {
  width: 100%;
}

@media (max-width: 1420px) {
  .developer-toolbar {
    grid-template-columns: minmax(150px, 0.4fr) minmax(280px, 1fr) auto;
  }

  .environment-option.is-disabled {
    display: none;
  }

  .developer-workspace {
    grid-template-columns: 210px minmax(0, 1fr);
  }

  .document-aside {
    display: none;
  }
}

@media (max-width: 1180px) {
  .developer-toolbar {
    grid-template-columns: minmax(260px, 1fr) auto;
  }

  .toolbar-context {
    display: none;
  }
}

@media (max-width: 860px) {
  .developer-toolbar {
    position: static;
    grid-template-columns: 1fr;
  }

  .toolbar-actions {
    justify-content: space-between;
  }

  .developer-mobile-nav {
    display: block;
  }

  .developer-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .docs-navigation {
    display: none;
  }

  .document-article {
    padding: 24px;
  }
}

@media (max-width: 680px) {
  .toolbar-actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .environment-switch {
    width: 100%;
  }

  .environment-option.is-disabled {
    display: inline-flex;
  }

  .document-title-row {
    display: grid;
  }

  .document-actions {
    width: 100%;
  }

  .document-actions .el-button {
    flex: 1;
  }

  .document-meta {
    flex-wrap: wrap;
  }

  .endpoint-head {
    display: none;
  }

  .endpoint-table article {
    grid-template-columns: 1fr auto;
    gap: 8px 12px;
    padding: 13px;
  }

  .endpoint-route {
    grid-column: 1 / -1;
  }

  .endpoint-summary {
    grid-column: 1;
  }

  .endpoint-permission {
    grid-column: 1;
  }

  .endpoint-table article > .el-tag {
    grid-column: 2;
    grid-row: 2 / span 2;
    align-self: center;
  }

  .resource-links article {
    grid-template-columns: minmax(0, 1fr);
  }

  .resource-link-actions {
    padding-left: 47px;
  }
}

@media (max-width: 480px) {
  .developer-toolbar {
    padding: 10px;
  }

  .search-shortcut {
    display: none;
  }

  .document-article {
    padding: 20px 16px 24px;
  }

  .document-title-row h1 {
    font-size: 25px;
  }

  .document-actions {
    flex-direction: column;
  }

  .document-step-title {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }

  .document-pager {
    grid-template-columns: 1fr;
  }
}
</style>
