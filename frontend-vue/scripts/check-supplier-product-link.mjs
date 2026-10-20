import assert from "node:assert/strict";
import test from "node:test";

import {
  normalizeSupplierProductLinkInput,
  XFS_INTERNAL_PRODUCT_CODE_RULE
} from "../src/utils/ecommerce-url.ts";

test("鑫方盛内部编号转换为商品详情页", () => {
  for (const code of ["23629538", "10346797", "12001336", "22486741", "13202030"]) {
    const result = normalizeSupplierProductLinkInput(code, XFS_INTERNAL_PRODUCT_CODE_RULE);

    assert.equal(result.error, undefined);
    assert.equal(result.value, `https://vip.xfs.com/vip/productsku/${code}.html`);
  }
});

test("其他供应商不能套用鑫方盛内部编号", () => {
  const result = normalizeSupplierProductLinkInput("23629538", null);

  assert.match(result.error || "", /未配置内部商品编号规则/);
});

test("普通商品链接仍按原规则转换", () => {
  const result = normalizeSupplierProductLinkInput(
    "https://item.m.jd.com/product/100104072733.html?utm_source=share",
    null
  );

  assert.equal(result.value, "https://item.jd.com/100104072733.html");
});
