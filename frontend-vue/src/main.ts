import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import { registerPermissionDirective } from "./directives/permission";
import { initializeRuntimeEnvironment } from "./config/runtime-environment";
import "./styles.css";

const chunkRetryStorageKey = "procurement-platform-chunk-retry-at";
const chunkRetryWindowMs = 60_000;
let fatalLoadErrorHandled = false;

function isChunkLoadError(error: unknown): boolean {
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error ?? "");
  return /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|ChunkLoadError|Loading chunk .* failed/i.test(
    message
  );
}

function claimAutomaticChunkRetry(): boolean {
  try {
    const lastRetryAt = Number(window.sessionStorage.getItem(chunkRetryStorageKey) ?? 0);
    const now = Date.now();
    if (Number.isFinite(lastRetryAt) && now - lastRetryAt < chunkRetryWindowMs) {
      return false;
    }
    window.sessionStorage.setItem(chunkRetryStorageKey, String(now));
    return true;
  } catch {
    return false;
  }
}

function clearAutomaticChunkRetry(): void {
  try {
    window.sessionStorage.removeItem(chunkRetryStorageKey);
  } catch {
    // Storage may be disabled by the browser; a successful page load needs no further action.
  }
}

function renderLoadFailure(): void {
  const root = document.querySelector<HTMLElement>("#app");
  if (!root) {
    return;
  }
  root.innerHTML = `
    <main style="min-height:100vh;display:grid;place-items:center;padding:24px;background:#f5f7fa;color:#17233d;font-family:Arial,'Microsoft YaHei',sans-serif;box-sizing:border-box">
      <section style="width:min(420px,100%);padding:28px;border:1px solid #d9e2f2;background:#fff;border-radius:6px;box-shadow:0 8px 24px rgba(31,68,122,.08);text-align:center;box-sizing:border-box">
        <h1 style="margin:0 0 10px;font-size:22px;line-height:1.4">页面加载失败</h1>
        <p style="margin:0 0 20px;color:#667085;font-size:14px;line-height:1.7">页面资源暂时未能加载，请重新加载后继续使用。</p>
        <button type="button" style="height:36px;padding:0 20px;border:1px solid #1f5eff;border-radius:4px;background:#1f5eff;color:#fff;font-size:14px;cursor:pointer">重新加载</button>
      </section>
    </main>`;
  root.querySelector("button")?.addEventListener("click", () => {
    clearAutomaticChunkRetry();
    window.location.reload();
  });
}

function handleFatalLoadError(error: unknown): void {
  if (fatalLoadErrorHandled) {
    return;
  }
  fatalLoadErrorHandled = true;
  console.error("Frontend failed to load", error);

  if (isChunkLoadError(error) && claimAutomaticChunkRetry()) {
    window.location.reload();
    return;
  }
  renderLoadFailure();
}

async function bootstrap(): Promise<void> {
  await initializeRuntimeEnvironment();
  const [{ default: App }, { default: router }] = await Promise.all([
    import("./App.vue"),
    import("./router")
  ]);

  const app = createApp(App);
  app.use(createPinia());
  router.onError(handleFatalLoadError);
  app.use(router);
  app.use(ElementPlus);
  registerPermissionDirective(app);
  app.mount("#app");

  void router.isReady().then(clearAutomaticChunkRetry).catch(handleFatalLoadError);
}

window.addEventListener("vite:preloadError", (event) => {
  event.preventDefault();
  handleFatalLoadError((event as Event & { payload?: unknown }).payload ?? event);
});

void bootstrap().catch(handleFatalLoadError);
