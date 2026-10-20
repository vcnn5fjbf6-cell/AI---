import { randomBytes } from "node:crypto";
import { mkdir, open, readFile, rename, unlink } from "node:fs/promises";
import path from "node:path";

export function initialState() {
  return { version: 1, batches: [], idempotency: {} };
}

function validateState(state) {
  if (
    !state
    || state.version !== 1
    || !Array.isArray(state.batches)
    || !state.idempotency
    || typeof state.idempotency !== "object"
    || Array.isArray(state.idempotency)
  ) {
    throw new Error("Release state file is invalid");
  }
  return state;
}

export class AtomicStateStore {
  constructor(filePath = "/data/state.json") {
    this.filePath = filePath;
  }

  async load() {
    try {
      const content = await readFile(this.filePath, "utf8");
      return validateState(JSON.parse(content));
    } catch (error) {
      if (error?.code === "ENOENT") {
        return initialState();
      }
      throw error;
    }
  }

  async save(state) {
    validateState(state);
    const directory = path.dirname(this.filePath);
    await mkdir(directory, { recursive: true });
    const temporary = `${this.filePath}.${process.pid}.${randomBytes(6).toString("hex")}.tmp`;
    let handle;
    try {
      handle = await open(temporary, "wx", 0o600);
      await handle.writeFile(`${JSON.stringify(state, null, 2)}\n`, "utf8");
      await handle.sync();
      await handle.close();
      handle = undefined;
      await rename(temporary, this.filePath);
      let directoryHandle;
      try {
        directoryHandle = await open(directory, "r");
        await directoryHandle.sync();
      } catch {
        // Some filesystems do not support fsync on directories.
      } finally {
        await directoryHandle?.close().catch(() => {});
      }
    } catch (error) {
      if (handle) {
        await handle.close().catch(() => {});
      }
      await unlink(temporary).catch(() => {});
      throw error;
    }
  }
}
