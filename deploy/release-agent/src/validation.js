import { createHash, timingSafeEqual } from "node:crypto";

import { PublicError } from "./errors.js";

const IDP_KEY_PATTERN = /^[A-Za-z0-9._:-]{8,128}$/;
const BATCH_ID_PATTERN = /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
const SHA_PATTERN = /^[a-f0-9]{40}$/;

function objectBody(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new PublicError(400, "INVALID_BODY", "请求体必须是 JSON 对象");
  }
  return value;
}

export function validateCreateInput(value) {
  const body = objectBody(value);
  if (!Array.isArray(body.prNumbers) || body.prNumbers.length < 1 || body.prNumbers.length > 10) {
    throw new PublicError(400, "INVALID_PR_NUMBERS", "prNumbers 必须包含 1 至 10 个 PR 编号");
  }
  const prNumbers = body.prNumbers.map((number) => {
    if (!Number.isSafeInteger(number) || number < 1) {
      throw new PublicError(400, "INVALID_PR_NUMBERS", "PR 编号必须是正整数");
    }
    return number;
  }).sort((left, right) => left - right);
  if (new Set(prNumbers).size !== prNumbers.length) {
    throw new PublicError(400, "DUPLICATE_PR_NUMBER", "prNumbers 不能包含重复编号");
  }
  return {
    prNumbers,
    idempotencyKey: validateIdempotencyKey(body.idempotencyKey),
    requestedBy: validateRequestedBy(body.requestedBy),
  };
}

export function validatePromoteInput(value) {
  const body = objectBody(value);
  if (body.confirmation !== "DEPLOY_PRODUCTION") {
    throw new PublicError(400, "INVALID_CONFIRMATION", "生产发布确认词不正确");
  }
  return {
    confirmation: body.confirmation,
    idempotencyKey: validateIdempotencyKey(body.idempotencyKey),
    requestedBy: validateRequestedBy(body.requestedBy),
  };
}

export function validateIdempotencyKey(value) {
  if (typeof value !== "string" || !IDP_KEY_PATTERN.test(value)) {
    throw new PublicError(400, "INVALID_IDEMPOTENCY_KEY", "idempotencyKey 格式不正确");
  }
  return value;
}

export function validateRequestedBy(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new PublicError(400, "INVALID_REQUESTED_BY", "requestedBy 格式不正确");
  }
  const validUserId = Number.isSafeInteger(value.userId)
    ? value.userId >= 1
    : typeof value.userId === "string" && /^[A-Za-z0-9._:-]{1,80}$/.test(value.userId);
  const validText = (text, maximum, allowEmpty = false) => typeof text === "string"
    && text.length <= maximum
    && (allowEmpty || text.length > 0)
    && !/[\u0000-\u001f\u007f]/.test(text);
  if (
    !validUserId
    || !validText(value.username, 100)
    || !validText(value.realName, 100)
    || !validText(value.clientIp, 64)
    || !validText(value.userAgent, 512, true)
  ) {
    throw new PublicError(400, "INVALID_REQUESTED_BY", "requestedBy 格式不正确");
  }
  return {
    userId: value.userId,
    username: value.username,
    realName: value.realName,
    clientIp: value.clientIp,
    userAgent: value.userAgent,
  };
}

export function validateBatchId(value) {
  if (typeof value !== "string" || !BATCH_ID_PATTERN.test(value)) {
    throw new PublicError(404, "BATCH_NOT_FOUND", "发布批次不存在");
  }
  return value;
}

export function validateSha(value, label = "SHA") {
  if (typeof value !== "string" || !SHA_PATTERN.test(value)) {
    throw new Error(`${label} is not a full commit SHA`);
  }
  return value;
}

export function requestHash(value) {
  const stable = (item) => {
    if (Array.isArray(item)) {
      return item.map(stable);
    }
    if (item && typeof item === "object") {
      return Object.fromEntries(Object.keys(item).sort().map((key) => [key, stable(item[key])]));
    }
    return item;
  };
  return createHash("sha256").update(JSON.stringify(stable(value))).digest("hex");
}

function digest(value) {
  return createHash("sha256").update(value).digest();
}

function parseBasicCredentials(authorizationHeader) {
  if (typeof authorizationHeader !== "string") {
    return null;
  }
  const match = /^Basic ([A-Za-z0-9+/]+={0,2})$/i.exec(authorizationHeader);
  if (!match) {
    return null;
  }
  try {
    const decoded = Buffer.from(match[1], "base64").toString("utf8");
    const separator = decoded.indexOf(":");
    if (separator < 1) {
      return null;
    }
    return {
      username: decoded.slice(0, separator),
      password: decoded.slice(separator + 1),
    };
  } catch {
    return null;
  }
}

export function basicAuthMatches(expectedUsername, expectedPassword, authorizationHeader) {
  const credentials = parseBasicCredentials(authorizationHeader) || { username: "", password: "" };
  return timingSafeEqual(digest(expectedUsername), digest(credentials.username))
    && timingSafeEqual(digest(expectedPassword), digest(credentials.password));
}
