export interface EcommerceUrlNormalizationResult {
  value: string;
  changed: boolean;
  shortLink: boolean;
  error?: string;
}

export const XFS_INTERNAL_PRODUCT_CODE_RULE = "XFS_INTERNAL_SKU";

const XFS_PRODUCT_URL_PREFIX = "https://vip.xfs.com/vip/productsku/";
const INTERNAL_PRODUCT_CODE_PATTERN = /^\d{1,32}$/;

const URL_PATTERN = /https?:\/\/[^\s\u3000<>"'，。；;！!]+/i;
const TRAILING_PUNCTUATION = /[\])}】）>,，。；;！!]+$/;
const SHORT_LINK_HOSTS = new Set(["3.cn", "u.jd.com", "m.tb.cn"]);
const BLOCKED_LOGIN_HOSTS = new Set([
  "login.taobao.com",
  "passport.taobao.com",
  "login.tmall.com",
  "passport.tmall.com",
  "login.jd.com",
  "passport.jd.com",
  "plogin.m.jd.com"
]);

export function normalizeEcommerceUrlInput(rawValue?: string | null): EcommerceUrlNormalizationResult {
  const raw = String(rawValue ?? "").trim();
  if (!raw) {
    return { value: "", changed: false, shortLink: false };
  }
  const matchedUrl = raw.match(URL_PATTERN)?.[0]?.replace(TRAILING_PUNCTUATION, "") || "";
  if (!matchedUrl) {
    return invalid("未识别到有效网址，请粘贴商品详情页链接");
  }

  let url: URL;
  try {
    url = new URL(matchedUrl);
  } catch {
    return invalid("链接格式不正确，请重新复制商品详情页地址");
  }
  const host = url.hostname.toLowerCase();
  if (!/^https?:$/.test(url.protocol) || !host || url.username || url.password) {
    return invalid("链接格式不正确，请粘贴以 http:// 或 https:// 开头的商品详情页");
  }
  if (BLOCKED_LOGIN_HOSTS.has(host)) {
    return invalid("当前地址是登录或身份校验页面，不是商品详情页");
  }

  let normalized = matchedUrl;
  let shortLink = false;
  if (SHORT_LINK_HOSTS.has(host)) {
    shortLink = true;
  } else if (host === "item.jd.com" || host === "item.m.jd.com") {
    const skuId = jdSkuId(url);
    if (!skuId) {
      return invalid("未从京东链接中识别到商品编号");
    }
    normalized = `https://item.jd.com/${skuId}.html`;
  } else if (host === "detail.tmall.com" || host === "m.tmall.com") {
    const itemId = numericSearchParam(url, "id");
    if (!itemId) {
      return invalid("未从天猫链接中识别到商品编号");
    }
    const skuId = numericSearchParam(url, "skuId");
    normalized = `https://detail.tmall.com/item.htm?id=${itemId}${skuId ? `&skuId=${skuId}` : ""}`;
  } else if (host === "item.taobao.com") {
    const itemId = numericSearchParam(url, "id");
    if (!itemId) {
      return invalid("未从淘宝链接中识别到商品编号");
    }
    normalized = `https://item.taobao.com/item.htm?id=${itemId}`;
  } else if (matchedUrl.length > 1000) {
    return invalid("链接过长，请粘贴不带广告参数的商品详情页");
  }

  return {
    value: normalized,
    changed: normalized !== raw,
    shortLink
  };
}

export function normalizeSupplierProductLinkInput(
  rawValue?: string | null,
  internalProductCodeRule?: string | null
): EcommerceUrlNormalizationResult {
  const raw = String(rawValue ?? "").trim();
  if (INTERNAL_PRODUCT_CODE_PATTERN.test(raw)) {
    if (internalProductCodeRule === XFS_INTERNAL_PRODUCT_CODE_RULE) {
      return {
        value: `${XFS_PRODUCT_URL_PREFIX}${raw}.html`,
        changed: true,
        shortLink: false
      };
    }
    return invalid("当前供应商未配置内部商品编号规则，请填写商品详情页链接");
  }
  return normalizeEcommerceUrlInput(raw);
}

export function ecommerceUrlHref(rawValue?: string | null): string {
  const result = normalizeEcommerceUrlInput(rawValue);
  return result.error ? "" : result.value;
}

function jdSkuId(url: URL): string {
  const pathMatch = url.pathname.match(/^\/(?:product\/)?(\d+)\.html$/);
  if (pathMatch?.[1]) {
    return pathMatch[1];
  }
  return numericSearchParam(url, "wareId") || numericSearchParam(url, "sku");
}

function numericSearchParam(url: URL, name: string): string {
  let matchedValue = "";
  url.searchParams.forEach((value, key) => {
    if (!matchedValue && key.toLowerCase() === name.toLowerCase() && /^\d+$/.test(value)) {
      matchedValue = value;
    }
  });
  return matchedValue;
}

function invalid(error: string): EcommerceUrlNormalizationResult {
  return { value: "", changed: false, shortLink: false, error };
}
