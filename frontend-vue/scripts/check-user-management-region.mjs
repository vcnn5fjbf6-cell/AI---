import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = readFileSync(resolve(root, "src/pages/system/UserManagement.vue"), "utf8");

const formStart = source.indexOf("const createDefaultForm = ()");
const formEnd = source.indexOf("\n});", formStart);
const formSource = source.slice(formStart, formEnd);
const submitStart = source.indexOf("async function handleSubmit()");
const submitEnd = source.indexOf("\nasync function createUserWithEmailClearConfirm", submitStart);
const submitSource = source.slice(submitStart, submitEnd);

const errors = [];
if (formStart < 0 || formEnd < 0 || !formSource.includes("regionId: null")) {
  errors.push("new users must not default to a hard-coded production region ID");
}
if (submitStart < 0 || submitEnd < 0 || !submitSource.includes("regionId: formModel.regionId ?? null")) {
  errors.push("user submission must let the backend resolve a missing region from the current account");
}
if (formSource.includes("regionId: 1") || submitSource.includes("regionId: formModel.regionId || 1")) {
  errors.push("user management still contains the legacy region ID 1 fallback");
}

if (errors.length > 0) {
  console.error("User management region check failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("User management region check passed");
