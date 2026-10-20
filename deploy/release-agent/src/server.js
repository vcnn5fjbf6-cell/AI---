import { readFileSync } from "node:fs";
import { createServer } from "node:http";

import { PublicError } from "./errors.js";
import { basicAuthMatches } from "./validation.js";

const MAX_BODY_BYTES = 64 * 1024;
const UI_HTML = readFileSync(new URL("./ui.html", import.meta.url));
const UI_CSS = readFileSync(new URL("./ui.css", import.meta.url));
const UI_JS = readFileSync(new URL("./ui.js", import.meta.url));
function contentSecurityPolicy(frameAncestors) {
  return `default-src 'self'; frame-ancestors ${frameAncestors}; object-src 'none'; base-uri 'none'; form-action 'self'`;
}

function securityHeaders(contentType, frameAncestors) {
  const headers = {
    "Cache-Control": "no-store",
    "Content-Security-Policy": contentSecurityPolicy(frameAncestors),
    "Content-Type": contentType,
    "Cross-Origin-Opener-Policy": "same-origin",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
    "Referrer-Policy": "no-referrer",
    "X-Content-Type-Options": "nosniff",
  };
  if (frameAncestors === "'none'") {
    headers["X-Frame-Options"] = "DENY";
  }
  return headers;
}

function send(response, status, payload, contentType, additionalHeaders = {}, frameAncestors = "'none'") {
  response.writeHead(status, {
    ...securityHeaders(contentType, frameAncestors),
    "Content-Length": payload.length,
    ...additionalHeaders,
  });
  response.end(payload);
}

function sendJson(response, status, value, additionalHeaders = {}, frameAncestors = "'none'") {
  send(
    response,
    status,
    Buffer.from(JSON.stringify(value)),
    "application/json; charset=utf-8",
    additionalHeaders,
    frameAncestors,
  );
}

async function readJson(request) {
  if (!String(request.headers["content-type"] || "").toLowerCase().startsWith("application/json")) {
    throw new PublicError(415, "JSON_REQUIRED", "Content-Type 必须是 application/json");
  }
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) {
      throw new PublicError(413, "BODY_TOO_LARGE", "请求体过大");
    }
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new PublicError(400, "INVALID_JSON", "JSON 格式不正确");
  }
}

function requestedBy(request, adminUsername) {
  const userAgent = String(request.headers["user-agent"] || "")
    .replace(/[\u0000-\u001f\u007f]/g, "")
    .slice(0, 512);
  return {
    userId: adminUsername,
    username: adminUsername,
    realName: adminUsername,
    clientIp: String(request.socket.remoteAddress || "unknown").slice(0, 64),
    userAgent,
  };
}

function withAuditActor(body, request, adminUsername) {
  return {
    ...body,
    requestedBy: requestedBy(request, adminUsername),
  };
}

export function createReleaseAgentServer({ service, adminUsername, adminPassword, frameAncestors = "'none'", logger = console }) {
  return createServer(async (request, response) => {
    try {
      const url = new URL(request.url || "/", "http://release-control.local");
      if (url.search) {
        throw new PublicError(404, "NOT_FOUND", "请求路径不存在");
      }
      if (request.method === "GET" && url.pathname === "/health") {
        sendJson(response, 200, { status: "ok" }, {}, frameAncestors);
        return;
      }
      if (!basicAuthMatches(adminUsername, adminPassword, request.headers.authorization)) {
        throw new PublicError(401, "UNAUTHORIZED", "需要发布管理员身份验证");
      }
      if (request.method === "GET" && url.pathname === "/") {
        send(response, 200, UI_HTML, "text/html; charset=utf-8", {}, frameAncestors);
        return;
      }
      if (request.method === "GET" && url.pathname === "/assets/app.css") {
        send(response, 200, UI_CSS, "text/css; charset=utf-8", {}, frameAncestors);
        return;
      }
      if (request.method === "GET" && url.pathname === "/assets/app.js") {
        send(response, 200, UI_JS, "text/javascript; charset=utf-8", {}, frameAncestors);
        return;
      }
      if (request.method === "GET" && url.pathname === "/api/context") {
        sendJson(response, 200, await service.getContext(), {}, frameAncestors);
        return;
      }
      if (request.method === "GET" && url.pathname === "/api/batches") {
        sendJson(response, 200, { batches: service.getBatches() }, {}, frameAncestors);
        return;
      }
      if (request.method === "POST" && url.pathname === "/api/batches") {
        const body = withAuditActor(await readJson(request), request, adminUsername);
        const result = await service.createBatch(body);
        sendJson(response, result.replayed ? 200 : 201, result, {}, frameAncestors);
        return;
      }
      const promoteMatch = /^\/api\/batches\/([^/]+)\/promote$/.exec(url.pathname);
      if (request.method === "POST" && promoteMatch) {
        const body = withAuditActor(await readJson(request), request, adminUsername);
        const result = await service.promoteBatch(promoteMatch[1], body);
        sendJson(response, result.replayed ? 200 : 202, result, {}, frameAncestors);
        return;
      }
      const retryMatch = /^\/api\/batches\/([^/]+)\/retry$/.exec(url.pathname);
      if (request.method === "POST" && retryMatch) {
        const body = withAuditActor(await readJson(request), request, adminUsername);
        const result = await service.retryBatch(retryMatch[1], body);
        sendJson(response, result.replayed ? 200 : 202, result, {}, frameAncestors);
        return;
      }
      throw new PublicError(404, "NOT_FOUND", "请求路径不存在");
    } catch (error) {
      if (error instanceof PublicError) {
        const headers = error.status === 401
          ? { "WWW-Authenticate": 'Basic realm="Release Control", charset="UTF-8"' }
          : {};
        sendJson(response, error.status, { error: { code: error.code, message: error.message } }, headers, frameAncestors);
        return;
      }
      logger.error("Release control request failed", error?.message || error?.name || "unknown error");
      sendJson(response, 500, { error: { code: "INTERNAL_ERROR", message: "发布控制面内部错误" } }, {}, frameAncestors);
    }
  });
}
