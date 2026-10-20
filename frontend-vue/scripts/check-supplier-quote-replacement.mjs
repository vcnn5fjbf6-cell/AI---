import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const frontendRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const quotePage = readFileSync(resolve(frontendRoot, "src/pages/supplier/QuoteList.vue"), "utf8");
const errors = [];

const requiredMarkers = [
  'ref="quoteTableRef"',
  'row-key="purchaseRequestItemId"',
  '@change="handleReplacementFlagChange(row)"',
  'label="原商品报价单价"',
  'label="原商品报价金额"',
  'label="报价备注"',
  'class="replacement-field-error"',
  "await revealReplacementLine(invalidReplacementLine, true);",
  "toggleRowExpansion(line, true)",
  "input?.focus();"
];

for (const marker of requiredMarkers) {
  if (!quotePage.includes(marker)) {
    errors.push(`manual quote replacement interaction is missing: ${marker}`);
  }
}

const importMarkers = [
  '@click="openImportDialog"',
  '@change="handleQuoteImportFileChange"',
  "importQuotePreviewApi",
  "async function handleQuoteImportFileChange"
];

for (const marker of importMarkers) {
  if (!quotePage.includes(marker)) {
    errors.push(`existing batch quote import entry must remain unchanged: ${marker}`);
  }
}

if (errors.length > 0) {
  console.error("Supplier quote replacement interaction check failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Supplier quote replacement interaction check passed");
