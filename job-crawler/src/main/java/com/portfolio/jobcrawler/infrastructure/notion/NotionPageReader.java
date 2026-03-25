package com.portfolio.jobcrawler.infrastructure.notion;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Notion 공개 페이지의 텍스트 + 이미지를 추출한다.
 * 공개 설정된 Notion 페이지만 크롤링 가능. 비공개 페이지는 실패 반환.
 */
@Slf4j
@Component
public class NotionPageReader {

    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_CONTENT_LENGTH = 10_000;
    private static final int MAX_IMAGES = 10;

    /**
     * Notion 페이지 크롤링 결과 (텍스트 + 이미지 URL 목록)
     */
    @Getter
    public static class NotionContent {
        private final String text;
        private final List<String> imageUrls;

        public NotionContent(String text, List<String> imageUrls) {
            this.text = text;
            this.imageUrls = imageUrls;
        }

        public boolean isEmpty() {
            return text.isBlank() && imageUrls.isEmpty();
        }
    }

    /**
     * Notion 공개 페이지의 텍스트와 이미지를 추출하여 반환한다.
     */
    public NotionContent readPage(String notionUrl) {
        if (notionUrl == null || notionUrl.isBlank() || !isNotionUrl(notionUrl)) {
            return new NotionContent("", List.of());
        }

        try {
            log.info("[Notion] 페이지 크롤링 시작: {}", notionUrl);

            Document doc = Jsoup.connect(notionUrl)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(TIMEOUT_MS)
                    .get();

            String content = extractContent(doc);
            List<String> images = extractImageUrls(doc);

            if (content.isBlank() && images.isEmpty()) {
                log.warn("[Notion] 콘텐츠 추출 불가 (비공개이거나 빈 페이지): {}", notionUrl);
                return new NotionContent("", List.of());
            }

            log.info("[Notion] 크롤링 완료: {}자, 이미지 {}개", content.length(), images.size());
            return new NotionContent(content, images);

        } catch (Exception e) {
            log.error("[Notion] 크롤링 실패: {} - {}", notionUrl, e.getMessage());
            return new NotionContent("", List.of());
        }
    }

    /** 하위 호환용 — 텍스트만 반환 */
    public String readPageContent(String notionUrl) {
        return readPage(notionUrl).getText();
    }

    private boolean isNotionUrl(String url) {
        String lower = url.toLowerCase();
        return lower.contains("notion.so") || lower.contains("notion.site");
    }

    private List<String> extractImageUrls(Document doc) {
        List<String> urls = new ArrayList<>();

        // Notion 콘텐츠 영역의 이미지
        Elements images = doc.select(
                "[class*=notion-page-content] img, " +
                "[class*=notion-image-block] img, " +
                "article img, main img, [role='main'] img"
        );

        for (Element img : images) {
            String src = img.absUrl("src");
            if (src.isBlank()) src = img.attr("src");
            if (src.isBlank()) continue;

            // Notion 아이콘/이모지/작은 UI 이미지 제외
            if (isContentImage(src, img)) {
                urls.add(src);
                if (urls.size() >= MAX_IMAGES) break;
            }
        }

        return urls;
    }

    private boolean isContentImage(String src, Element img) {
        // Notion 시스템 이미지 제외
        if (src.contains("/emoji/") || src.contains("/icons/")) return false;
        if (src.contains("notion-static.com/icons")) return false;
        if (src.contains("favicon")) return false;

        // 너무 작은 이미지 제외 (아이콘일 가능성)
        String width = img.attr("width");
        String height = img.attr("height");
        if (!width.isBlank() && !height.isBlank()) {
            try {
                int w = Integer.parseInt(width.replaceAll("[^0-9]", ""));
                int h = Integer.parseInt(height.replaceAll("[^0-9]", ""));
                if (w < 50 || h < 50) return false;
            } catch (NumberFormatException ignored) {}
        }

        return true;
    }

    private String extractContent(Document doc) {
        StringBuilder sb = new StringBuilder();

        Element title = doc.selectFirst("title");
        if (title != null && !title.text().isBlank()) {
            sb.append("제목: ").append(title.text().trim()).append("\n\n");
        }

        Elements contentBlocks = doc.select(
                "[class*=notion-page-content] [class*=notion-text-block], " +
                "[class*=notion-page-content] [class*=notion-header-block], " +
                "[class*=notion-page-content] [class*=notion-sub_header-block], " +
                "[class*=notion-page-content] [class*=notion-bulleted_list], " +
                "[class*=notion-page-content] [class*=notion-numbered_list], " +
                "[class*=notion-page-content] [class*=notion-toggle-block], " +
                "[class*=notion-page-content] [class*=notion-code-block], " +
                "[class*=notion-page-content] [class*=notion-callout-block]"
        );

        if (!contentBlocks.isEmpty()) {
            for (Element block : contentBlocks) {
                String text = block.text().trim();
                if (!text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
        } else {
            Element article = doc.selectFirst("article, main, [role='main']");
            if (article != null) {
                sb.append(article.text().trim());
            } else {
                Element body = doc.body();
                if (body != null) {
                    body.select("nav, footer, header, script, style, noscript").remove();
                    String bodyText = body.text().trim();
                    if (bodyText.length() > 100) {
                        sb.append(bodyText);
                    }
                }
            }
        }

        String result = sb.toString().trim();
        if (result.length() > MAX_CONTENT_LENGTH) {
            result = result.substring(0, MAX_CONTENT_LENGTH) + "\n...(이하 생략)";
        }
        return result;
    }
}
