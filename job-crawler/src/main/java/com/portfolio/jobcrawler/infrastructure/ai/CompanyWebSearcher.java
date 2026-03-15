package com.portfolio.jobcrawler.infrastructure.ai;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 회사명으로 웹 검색하여 기업 정보를 수집하는 서비스.
 * 네이버/구글 검색 결과에서 스니펫을 추출하여 AI 분석의 컨텍스트로 활용.
 */
@Slf4j
@Component
public class CompanyWebSearcher {

    private static final String NAVER_SEARCH_URL = "https://search.naver.com/search.naver?query=";
    private static final String GOOGLE_SEARCH_URL = "https://www.google.com/search?q=";
    private static final int TIMEOUT_MS = 10_000;

    /**
     * 회사명으로 웹 검색하여 기업 정보 텍스트를 수집.
     * 검색 실패 시 빈 문자열 반환 (AI가 자체 지식으로 보완).
     */
    public String searchCompanyInfo(String companyName) {
        StringBuilder result = new StringBuilder();

        // 1. 네이버 기업 정보 검색
        String naverInfo = searchNaver(companyName + " 기업정보 연봉 평판");
        if (!naverInfo.isEmpty()) {
            result.append("[네이버 검색 결과]\n").append(naverInfo).append("\n\n");
        }

        // 2. 네이버 기업 리뷰 검색
        String reviewInfo = searchNaver(companyName + " 기업리뷰 직원 후기");
        if (!reviewInfo.isEmpty()) {
            result.append("[기업 리뷰 검색 결과]\n").append(reviewInfo).append("\n\n");
        }

        if (result.isEmpty()) {
            // 폴백: 구글 검색
            String googleInfo = searchGoogle(companyName + " 기업정보 연봉 리뷰");
            if (!googleInfo.isEmpty()) {
                result.append("[구글 검색 결과]\n").append(googleInfo).append("\n\n");
            }
        }

        log.info("[CompanyWebSearcher] '{}' 검색 완료 - {}자 수집", companyName, result.length());
        return result.toString().trim();
    }

    private String searchNaver(String query) {
        try {
            String url = NAVER_SEARCH_URL + URLEncoder.encode(query, StandardCharsets.UTF_8);
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(TIMEOUT_MS)
                    .get();

            List<String> snippets = new ArrayList<>();

            // 지식백과 / 기업 정보 카드
            Elements infoCards = doc.select(".info_group .info_pair, .detail_box .txt, .api_subject_bx .desc");
            for (Element el : infoCards) {
                String text = el.text().trim();
                if (!text.isEmpty() && text.length() > 10) {
                    snippets.add(text);
                }
            }

            // 검색 결과 스니펫
            Elements searchResults = doc.select(".total_dsc_wrap, .api_txt_lines, .dsc_txt");
            for (Element el : searchResults) {
                String text = el.text().trim();
                if (!text.isEmpty() && text.length() > 20) {
                    snippets.add(text);
                    if (snippets.size() >= 8) break;
                }
            }

            return String.join("\n", snippets);
        } catch (Exception e) {
            log.debug("[CompanyWebSearcher] 네이버 검색 실패: {}", e.getMessage());
            return "";
        }
    }

    private String searchGoogle(String query) {
        try {
            String url = GOOGLE_SEARCH_URL + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&hl=ko";
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(TIMEOUT_MS)
                    .get();

            List<String> snippets = new ArrayList<>();

            // 검색 결과 스니펫
            Elements results = doc.select(".VwiC3b, .IsZvec, .aCOpRe span");
            for (Element el : results) {
                String text = el.text().trim();
                if (!text.isEmpty() && text.length() > 20) {
                    snippets.add(text);
                    if (snippets.size() >= 6) break;
                }
            }

            return String.join("\n", snippets);
        } catch (Exception e) {
            log.debug("[CompanyWebSearcher] 구글 검색 실패: {}", e.getMessage());
            return "";
        }
    }
}
