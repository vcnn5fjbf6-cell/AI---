import { useAuthStore } from "../stores/auth";

export function hasPermission(required: string | string[]): boolean {
  const authStore = useAuthStore();
  const requiredList = Array.isArray(required) ? required : [required];
  if (requiredList.length === 0) {
    return true;
  }
  if (!authStore.user) {
    return false;
  }
  const userPerms = authStore.permissionCodes;
  if (!userPerms || userPerms.length === 0) {
    return false;
  }
  if (userPerms.includes("*:*:*") || userPerms.includes("*")) {
    return true;
  }
  return requiredList.some((perm) => userPerms.includes(perm));
}
