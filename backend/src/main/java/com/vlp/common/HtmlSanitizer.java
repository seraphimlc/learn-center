package com.vlp.common;

import java.util.regex.Pattern;

/**
 * 轻量存储型 XSS 收敛：去掉 script/iframe/object/事件属性。
 * 非完整 HTML 白名单；富文本若需更严可换专业库（jsoup）。
 */
public final class HtmlSanitizer {
    private static final Pattern SCRIPT = Pattern.compile("(?is)<script[^>]*>.*?</script>");
    private static final Pattern IFRAME = Pattern.compile("(?is)<iframe[^>]*>.*?</iframe>");
    private static final Pattern OBJECT = Pattern.compile("(?is)<object[^>]*>.*?</object>");
    private static final Pattern EMBED = Pattern.compile("(?is)<embed[^>]*>");
    private static final Pattern LINK_JS = Pattern.compile("(?is)<link[^>]+>");
    private static final Pattern EVENT_ATTR = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern JS_URI = Pattern.compile("(?i)(href|src)\\s*=\\s*([\"'])\\s*javascript:[^\\2]*\\2");

    private HtmlSanitizer() {}

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) return html;
        String s = html;
        s = SCRIPT.matcher(s).replaceAll("");
        s = IFRAME.matcher(s).replaceAll("");
        s = OBJECT.matcher(s).replaceAll("");
        s = EMBED.matcher(s).replaceAll("");
        s = LINK_JS.matcher(s).replaceAll("");
        s = EVENT_ATTR.matcher(s).replaceAll("");
        s = JS_URI.matcher(s).replaceAll("$1=$2#$2");
        return s;
    }
}
