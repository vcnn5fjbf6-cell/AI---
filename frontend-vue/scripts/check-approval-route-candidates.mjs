import assert from "node:assert/strict";
import test from "node:test";
import { parseIdList, resolveCandidateUserIdList } from "../src/utils/approval-route-candidates.ts";

test("V1-V8 的页面实时选择优先于尚未同步的持久化字段", () => {
  for (let levelNo = 1; levelNo <= 8; levelNo += 1) {
    assert.deepEqual(
      resolveCandidateUserIdList({ candidateUserIds: "", candidateUserIdList: [1000 + levelNo] }),
      [1000 + levelNo]
    );
  }
});

test("页面清空审批人后不会被旧审批人字段重新填回", () => {
  assert.deepEqual(resolveCandidateUserIdList({ candidateUserIds: "123", candidateUserIdList: [] }), []);
});

test("加载已有配置时继续解析后端审批人 ID", () => {
  assert.deepEqual(resolveCandidateUserIdList({ candidateUserIds: "11，12、13" }), [11, 12, 13]);
  assert.deepEqual(parseIdList("14 / 15 invalid"), [14, 15]);
});
