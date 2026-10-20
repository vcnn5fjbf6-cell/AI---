import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = readFileSync(resolve(root, "src/pages/procurementVnet/ProcurementRequestForm.vue"), "utf8");

const mountedStart = source.indexOf("onMounted(async () => {");
const mountedEnd = source.indexOf("\n});", mountedStart);
const mountedSource = source.slice(mountedStart, mountedEnd);
const loadDetailIndex = mountedSource.indexOf("await loadDetail();");
const loadDepartmentsIndex = mountedSource.indexOf("await loadDepartmentOptions();");

const saveStart = source.indexOf("async function saveRequest(");
const saveEnd = source.indexOf("\nfunction buildPayload", saveStart);
const saveSource = source.slice(saveStart, saveEnd);
const selectedDepartmentIndex = saveSource.indexOf("const selectedDepartment = departmentOptions.value.find");
const invalidSelectionIndex = saveSource.indexOf("if (!selectedDepartment)");
const applyDepartmentIndex = saveSource.indexOf("applyDepartmentRoute(selectedDepartment);");
const buildPayloadIndex = saveSource.indexOf("const payload = buildPayload();");

const errors = [];
if (mountedStart < 0 || mountedEnd < 0 || loadDetailIndex < 0 || loadDepartmentsIndex < 0) {
  errors.push("request form initialization steps are missing");
} else if (loadDetailIndex > loadDepartmentsIndex) {
  errors.push("request data must initialize before department options are matched");
}
if (selectedDepartmentIndex < 0 || invalidSelectionIndex < 0 || applyDepartmentIndex < 0) {
  errors.push("request submission must validate and apply the selected department route");
} else if (buildPayloadIndex < 0 || applyDepartmentIndex > buildPayloadIndex) {
  errors.push("selected department fields must be applied before building the request payload");
}

if (errors.length > 0) {
  console.error("Procurement request department check failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Procurement request department check passed");
