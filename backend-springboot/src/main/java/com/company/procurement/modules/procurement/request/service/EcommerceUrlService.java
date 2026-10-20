package com.company.procurement.modules.procurement.request.service;

import com.company.procurement.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EcommerceUrlService {

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)https?://[^\\s\\u3000<>\\\"'，。；;！!]+");
    private static final Pattern JD_ITEM_PATH = Pattern.compile("^/(?:product/)?(\\d+)\\.html$");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[\\])}】）>,，。；;！!]+$");
    private static final Set<String> SHORT_LINK_HOSTS = Set.of("3.cn", "u.jd.com", "m.tb.cn");
    private static final Set<String> BLOCKED_LOGIN_HOSTS = Set.of(
        "login.taobao.com",
        "passport.taobao.com",
        "login.tmall.com",
        "passport.tmall.com",
        "login.jd.com",
        "passport.jd.com",
        "plogin.m.jd.com"
    );

    private final ShortLinkResolver shortLinkResolver;

    @Value("${procurement.ecommerce.resolve-short-links:true}")
    private boolean resolveShortLinks = true;

    public EcommerceUrlService() {
        this(new HttpShortLinkResolver());
    }

    EcommerceUrlService(ShortLinkResolver shortLinkResolver) {
        this.shortLinkResolver = shortLinkResolver;
    }

    public String normalize(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "";
        }
        Matcher matcher = URL_PATTERN.matcher(rawValue.trim());
        if (!matcher.find()) {
            throw invalid("未识别到有效网址，请粘贴商品详情页链接");
        }
        String extracted = TRAILING_PUNCTUATION.matcher(matcher.group()).replaceAll("");
        return normalizeUri(parseUri(extracted), 0);
    }

    public String normalizeForLine(String rawValue, int lineNo) {
        try {
            return normalize(rawValue);
        } catch (BizException ex) {
            throw new BizException(ex.getCode(), "第 " + lineNo + " 行电商链接：" + ex.getMessage());
        }
    }

    private String normalizeUri(URI uri, int depth) {
        String scheme = lower(uri.getScheme());
        String host = lower(uri.getHost());
        if (!("http".equals(scheme) || "https".equals(scheme)) || !StringUtils.hasText(host) || uri.getUserInfo() != null) {
            throw invalid("链接格式不正确，请粘贴以 http:// 或 https:// 开头的商品详情页");
        }
        if (BLOCKED_LOGIN_HOSTS.contains(host)) {
            throw invalid("当前地址是登录或身份校验页面，不是商品详情页");
        }
        if (SHORT_LINK_HOSTS.contains(host)) {
            if (!resolveShortLinks) {
                return uri.toString();
            }
            if (depth >= 3) {
                throw invalid("短链接跳转次数过多，请打开后复制商品详情页地址");
            }
            try {
                return normalizeUri(shortLinkResolver.resolve(uri), depth + 1);
            } catch (BizException ex) {
                if ("ECOMMERCE_SHORT_LINK_RESOLVE_FAILED".equals(ex.getCode())) {
                    return uri.toString();
                }
                throw ex;
            }
        }
        if ("item.jd.com".equals(host) || "item.m.jd.com".equals(host)) {
            String skuId = jdSkuId(uri);
            if (!StringUtils.hasText(skuId)) {
                throw invalid("未从京东链接中识别到商品编号");
            }
            return "https://item.jd.com/" + skuId + ".html";
        }
        if ("detail.tmall.com".equals(host) || "m.tmall.com".equals(host)) {
            String itemId = numericQueryParam(uri, "id");
            if (!StringUtils.hasText(itemId)) {
                throw invalid("未从天猫链接中识别到商品编号");
            }
            String skuId = numericQueryParam(uri, "skuId");
            return "https://detail.tmall.com/item.htm?id=" + itemId
                + (StringUtils.hasText(skuId) ? "&skuId=" + skuId : "");
        }
        if ("item.taobao.com".equals(host)) {
            String itemId = numericQueryParam(uri, "id");
            if (!StringUtils.hasText(itemId)) {
                throw invalid("未从淘宝链接中识别到商品编号");
            }
            return "https://item.taobao.com/item.htm?id=" + itemId;
        }
        if (uri.toString().length() > 1000) {
            throw invalid("链接过长，请粘贴不带广告参数的商品详情页");
        }
        return uri.toString();
    }

    private String jdSkuId(URI uri) {
        Matcher matcher = JD_ITEM_PATH.matcher(uri.getPath() == null ? "" : uri.getPath());
        if (matcher.matches()) {
            return matcher.group(1);
        }
        String wareId = numericQueryParam(uri, "wareId");
        return StringUtils.hasText(wareId) ? wareId : numericQueryParam(uri, "sku");
    }

    private String numericQueryParam(URI uri, String expectedName) {
        String query = uri.getRawQuery();
        if (!StringUtils.hasText(query)) {
            return "";
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            String rawName = separator >= 0 ? part.substring(0, separator) : part;
            String rawValue = separator >= 0 ? part.substring(separator + 1) : "";
            try {
                String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
                String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                if (expectedName.equalsIgnoreCase(name) && value.matches("\\d+")) {
                    return value;
                }
            } catch (IllegalArgumentException ignored) {
                return "";
            }
        }
        return "";
    }

    private URI parseUri(String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException ex) {
            throw invalid("链接包含无法识别的字符，请重新复制商品详情页地址");
        }
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private BizException invalid(String message) {
        return new BizException("ECOMMERCE_URL_INVALID", message);
    }

    @FunctionalInterface
    interface ShortLinkResolver {
        URI resolve(URI shortLink);
    }

    private static final class HttpShortLinkResolver implements ShortLinkResolver {

        private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        @Override
        public URI resolve(URI shortLink) {
            URI current = shortLink;
            for (int attempt = 0; attempt < 3; attempt++) {
                ensureKnownShortHost(current);
                try {
                    HttpRequest request = HttpRequest.newBuilder(current)
                        .timeout(Duration.ofSeconds(8))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/138.0")
                        .GET()
                        .build();
                    HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                    if (response.statusCode() < 300 || response.statusCode() >= 400) {
                        throw shortLinkError();
                    }
                    String location = response.headers().firstValue("location").orElse("");
                    if (!StringUtils.hasText(location)) {
                        throw shortLinkError();
                    }
                    URI resolved = current.resolve(location);
                    String resolvedHost = lowerHost(resolved);
                    if (!SHORT_LINK_HOSTS.contains(resolvedHost)) {
                        return resolved;
                    }
                    current = resolved;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw shortLinkError();
                } catch (Exception ex) {
                    if (ex instanceof BizException bizException) {
                        throw bizException;
                    }
                    throw shortLinkError();
                }
            }
            throw shortLinkError();
        }

        private static void ensureKnownShortHost(URI uri) {
            if (!SHORT_LINK_HOSTS.contains(lowerHost(uri))) {
                throw shortLinkError();
            }
        }

        private static String lowerHost(URI uri) {
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        }

        private static BizException shortLinkError() {
            return new BizException(
                "ECOMMERCE_SHORT_LINK_RESOLVE_FAILED",
                "短链接暂时无法转换，请打开短链接后复制商品详情页地址"
            );
        }
    }
}
