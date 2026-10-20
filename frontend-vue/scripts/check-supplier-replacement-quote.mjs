import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const source = readFileSync(resolve(root, "src/pages/supplier/QuoteList.vue"), "utf8");

const requiredFragments = [
  "替换规格型号 <span class=\"required-label\">必填</span>",
  "{{ replacementLinkFieldLabel }} <span class=\"required-label\">必填</span>",
  "替换原因 <span class=\"required-label\">必填</span>",
  "replacementPriceDiffText(row)",
  "replacementPriceDiffType(row)",
  "确认提交替换方案",
  "替换商品名称（替换时必填）",
  "替换商品链接（替换时必填）",
  "normalizeSupplierProductLinkInput(",
  "quoteTarget.internalProductCodeRule",
  "parseOptionalProductCondition",
  "(replacementPrice - originalPrice) / originalPrice > 0.13"
];

const errors = requiredFragments
  .filter((fragment) => !source.includes(fragment))
  .map((fragment) => `missing supplier replacement quote behavior: ${fragment}`);

if (errors.length > 0) {
  console.error("Supplier replacement quote check failed:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log("Supplier replacement quote check passed");
