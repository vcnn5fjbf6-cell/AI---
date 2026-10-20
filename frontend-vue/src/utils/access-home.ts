export function resolveAccessHomePath(appCodes: string[] = [], _roleCodes: string[] = []): string {
  const normalizedAppCodes = appCodes.map((code) => code.trim().toUpperCase());
  if (normalizedAppCodes.length === 0) {
    return "/platform/access";
  }
  return "/workbench";
}

export function resolveInternalRedirect(value: unknown): string {
  if (typeof value !== "string") {
    return "";
  }
  if (!value.startsWith("/") || value.startsWith("//")) {
    return "";
  }
  return value;
}
