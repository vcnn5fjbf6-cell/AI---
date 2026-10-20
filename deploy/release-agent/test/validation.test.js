import assert from "node:assert/strict";
import test from "node:test";

import {
  basicAuthMatches,
  requestHash,
  validateBatchId,
  validateCreateInput,
  validatePromoteInput,
} from "../src/validation.js";
import { REQUESTED_BY } from "../test-support/helpers.js";

test("Basic authentication accepts only the exact username and password", () => {
  const header = `Basic ${Buffer.from("release-admin:high-entropy-password").toString("base64")}`;
  assert.equal(basicAuthMatches("release-admin", "high-entropy-password", header), true);
  assert.equal(basicAuthMatches("other-admin", "high-entropy-password", header), false);
  assert.equal(basicAuthMatches("release-admin", "wrong", header), false);
  assert.equal(basicAuthMatches("release-admin", "high-entropy-password", undefined), false);
});

test("create input validates PR bounds and preserves requestedBy audit fields", () => {
  const input = validateCreateInput({
    prNumbers: [7, 2],
    idempotencyKey: "create:123456",
    requestedBy: REQUESTED_BY,
  });
  assert.deepEqual(input.prNumbers, [2, 7]);
  assert.deepEqual(input.requestedBy, REQUESTED_BY);
  assert.throws(
    () => validateCreateInput({ prNumbers: [1, 1], idempotencyKey: "create:123456", requestedBy: REQUESTED_BY }),
    (error) => error.code === "DUPLICATE_PR_NUMBER",
  );
  assert.throws(
    () => validateCreateInput({ prNumbers: Array.from({ length: 11 }, (_, index) => index + 1), idempotencyKey: "create:123456", requestedBy: REQUESTED_BY }),
    (error) => error.code === "INVALID_PR_NUMBERS",
  );
});

test("promotion requires the production confirmation contract", () => {
  assert.equal(validatePromoteInput({
    confirmation: "DEPLOY_PRODUCTION",
    idempotencyKey: "promote:123456",
    requestedBy: REQUESTED_BY,
  }).confirmation, "DEPLOY_PRODUCTION");
  assert.throws(
    () => validatePromoteInput({ confirmation: "yes", idempotencyKey: "promote:123456", requestedBy: REQUESTED_BY }),
    (error) => error.code === "INVALID_CONFIRMATION",
  );
});

test("batch IDs are UUID v4 and request hashes are key-order independent", () => {
  assert.equal(validateBatchId("123e4567-e89b-42d3-a456-426614174000"), "123e4567-e89b-42d3-a456-426614174000");
  assert.throws(() => validateBatchId("release/batch-user-input"));
  assert.equal(requestHash({ a: 1, b: { c: 2, d: 3 } }), requestHash({ b: { d: 3, c: 2 }, a: 1 }));
});
