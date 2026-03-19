package com.portfolio.jobcrawler.application.crawler;

import com.portfolio.jobcrawler.domain.jobposting.entity.JobPosting;
import com.portfolio.jobcrawler.domain.jobposting.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostingUrlValidator {

    private final JobPostingRepository jobPostingRepository;

    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;
    private static final Set<String> EXPIRED_KEYWORDS = Set.of(
            "마감", "종료", "삭제", "not found", "페이지를 찾을 수 없습니다",
            "채용이 마감", "공고가 삭제", "만료된"
    );

    @Transactional
    public int closeStaleNoDeadlinePostings() {
        List<JobPosting> stalePostings = jobPostingRepository
                .findStaleNoDeadlinePostings(LocalDateTime.now().minusDays(7));

        if (stalePostings.isEmpty()) return 0;

        log.info("[URL검증] 마감일 없는 공고 {} 건 URL 유효성 검증 시작", stalePostings.size());

        int closedCount = 0;
        for (JobPosting posting : stalePostings) {
            if (isPostingExpired(posting.getUrl())) {
                posting.markAsClosed();
                closedCount++;
                log.info("[URL검증] 만료 공고 닫힘: {} - {}", posting.getCompany(), posting.getTitle());
            }
        }

        return closedCount;
    }

    private boolean isPostingExpired(String url) {
        if (url == null || url.isBlank()) return true;

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            int responseCode = conn.getResponseCode();

            if (responseCode == 404 || responseCode == 410 || responseCode == 403) {
                return true;
            }

            if (responseCode == 200 || responseCode == 301 || responseCode == 302) {
                String finalUrl = conn.getURL().toString();
                if (isRedirectedToMain(finalUrl, url)) {
                    return true;
                }

                String body = readResponseBody(conn, 2000);
                if (containsExpiredKeyword(body)) {
                    return true;
                }
            }

            conn.disconnect();
            return false;

        } catch (Exception e) {
            log.debug("[URL검증] URL 접근 실패 ({}): {}", url, e.getMessage());
            return false;
        }
    }

    private boolean isRedirectedToMain(String finalUrl, String originalUrl) {
        if (finalUrl.equals(originalUrl)) return false;

        String finalPath = URI.create(finalUrl).getPath();
        return finalPath == null || finalPath.equals("/") || finalPath.isEmpty()
                || finalPath.endsWith("/recruit") || finalPath.endsWith("/jobs");
    }

    private boolean containsExpiredKeyword(String body) {
        if (body == null || body.isEmpty()) return false;
        String lower = body.toLowerCase();
        return EXPIRED_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private String readResponseBody(HttpURLConnection conn, int maxChars) {
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            char[] buffer = new char[maxChars];
            int read = reader.read(buffer);
            return read > 0 ? new String(buffer, 0, read) : "";
        } catch (Exception e) {
            return "";
        }
    }
}
