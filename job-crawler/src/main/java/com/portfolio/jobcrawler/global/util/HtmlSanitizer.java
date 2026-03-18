package com.portfolio.jobcrawler.global.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * 크롤링된 HTML을 안전하게 소독한다.
 * 모든 인라인 style을 제거하여 우리 사이트 테마에 맞게 렌더링한다.
 */
public final class HtmlSanitizer {

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption", "col", "colgroup")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan")
            .addAttributes("table", "width", "border", "cellspacing", "cellpadding")
            .addAttributes("col", "width")
            .addAttributes("img", "src", "alt", "width", "height")
            .addProtocols("img", "src", "http", "https");
    // style 속성을 어디에도 허용하지 않음 → 인라인 CSS 전부 제거

    private HtmlSanitizer() {}

    /**
     * HTML을 소독하여 안전한 태그/속성만 남긴다.
     * 인라인 style은 전부 제거된다 (색상, 레이아웃, Tailwind 변수 등 문제 방지).
     */
    public static String sanitize(String dirtyHtml) {
        if (dirtyHtml == null || dirtyHtml.isBlank()) return "";
        return Jsoup.clean(dirtyHtml, SAFELIST);
    }

    /**
     * HTML 태그를 모두 제거하고 순수 텍스트만 반환한다.
     * AI 프롬프트 등에서 사용.
     */
    public static String toPlainText(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.clean(html, Safelist.none());
    }
}
