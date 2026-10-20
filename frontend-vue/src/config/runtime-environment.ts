let testEnvironment = import.meta.env.DEV;

export async function initializeRuntimeEnvironment(): Promise<void> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 3000);
  try {
    const response = await fetch("/", {
      method: "HEAD",
      cache: "no-store",
      credentials: "same-origin",
      signal: controller.signal
    });
    testEnvironment = import.meta.env.DEV
      || response.headers.get("X-Environment")?.trim().toLowerCase() === "test";
  } catch {
    testEnvironment = import.meta.env.DEV;
  } finally {
    clearTimeout(timeoutId);
  }
}

export function isTestEnvironment(): boolean {
  return testEnvironment;
}
