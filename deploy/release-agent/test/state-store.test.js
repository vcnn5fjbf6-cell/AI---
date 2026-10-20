import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import { AtomicStateStore, initialState } from "../src/state-store.js";

test("state store atomically persists valid JSON", async () => {
  const directory = await mkdtemp(path.join(os.tmpdir(), "release-agent-"));
  try {
    const file = path.join(directory, "state.json");
    const store = new AtomicStateStore(file);
    assert.deepEqual(await store.load(), initialState());
    const state = initialState();
    state.idempotency.example = { batchId: "123e4567-e89b-42d3-a456-426614174000" };
    await store.save(state);
    assert.deepEqual(await store.load(), state);
    assert.deepEqual(JSON.parse(await readFile(file, "utf8")), state);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
