import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = readFileSync(resolve(root, "src/pages/procurementVnet/ProcurementRequestForm.vue"), "utf8");

const errors = [];
const requiredFragments = [
  'precheckItemOpinionAt($index)',
  '@click="adoptPrecheckItem($index)"',
  '@click="adoptAllPrecheckItems"',
  '@click="optimizeItemPurpose"',
  'optimizeWorkflowPurposeApi(originalPurpose)',
  'itemForm.purpose = `${originalPurpose}\\n${optimizedContent}`',
  'isPrecheckItemSuggestionAdopted($index) ? "已采用" : "采用填写框架"',
  'purpose: purposeCompletionTemplate(item.purpose, itemOpinion.missingFields, itemOpinion.opinion)',
  '"现状问题及影响：【请补充】"',
  'QUANTITY_BASIS: "数量或覆盖依据：【请补充】"',
  'URGENCY_BASIS: "紧急影响及时限：【请补充】"',
  'containsPurposePlaceholder(itemForm.purpose)',
  'items.value.some((item) => containsPurposePlaceholder(item.purpose))'
];

for (const fragment of requiredFragments) {
  if (!source.includes(fragment)) {
    errors.push(`missing precheck behavior: ${fragment}`);
  }
}

const purposeColumn = source.indexOf('<el-table-column prop="purpose" label="采购原因"');
const opinionColumn = source.indexOf('<el-table-column label="AI预审意见"');
if (purposeColumn < 0 || opinionColumn < 0 || opinionColumn < purposeColumn) {
  errors.push("the per-item AI precheck opinion must appear after the purchase purpose column");
}

const applyItemStart = source.indexOf("function adoptPrecheckItem(");
const applyAllStart = source.indexOf("function adoptAllPrecheckItems(");
const applyEnd = source.indexOf("\nfunction containsPurposePlaceholder", applyAllStart);
const applySource = source.slice(applyItemStart, applyEnd);
if (applyItemStart < 0 || applyAllStart < 0 || applyEnd < 0
  || !applySource.includes("items.value[index] =")
  || !applySource.includes("items.value = items.value.map")) {
  errors.push("adopting a purpose suggestion must only update the in-memory draft items");
}
if (/createWorkflowRequestApi|updateWorkflowRequestApi|submitWorkflowRequestApi/.test(applySource)) {
  errors.push("adopting a precheck suggestion must not save or submit the request");
}
const fingerprintRefreshCount = applySource.split("precheckFingerprint.value = currentPrecheckFingerprint()").length - 1;
if (fingerprintRefreshCount < 2) {
  errors.push("single and bulk adoption must refresh the expected precheck fingerprint");
}

const optimizeStart = source.indexOf("async function optimizeItemPurpose(");
const optimizeEnd = source.indexOf("\nfunction saveLine", optimizeStart);
const optimizeSource = source.slice(optimizeStart, optimizeEnd);
if (optimizeStart < 0 || optimizeEnd < 0 || /createWorkflowRequestApi|updateWorkflowRequestApi|submitWorkflowRequestApi/.test(optimizeSource)) {
  errors.push("purpose optimization must only update the in-memory item draft");
}

if (errors.length > 0) {
  console.error("Procurement precheck check failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Procurement precheck check passed");
