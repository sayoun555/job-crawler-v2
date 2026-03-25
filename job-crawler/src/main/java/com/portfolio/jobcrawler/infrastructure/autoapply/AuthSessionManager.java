package com.portfolio.jobcrawler.infrastructure.autoapply;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Cookie;
import com.portfolio.jobcrawler.domain.account.entity.ExternalAccount;
import com.portfolio.jobcrawler.domain.account.repository.ExternalAccountRepository;
import com.portfolio.jobcrawler.domain.jobposting.vo.SourceSite;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 사용자별 외부 계정 세션(쿠키)을 관리한다.
 * DB가 원본, Redis는 캐시. Redis가 날아가면 DB에서 복구한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSessionManager {

    private final StringRedisTemplate redisTemplate;
    private final ExternalAccountRepository externalAccountRepository;
    private final ObjectMapper objectMapper;

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final int SESSION_EXPIRATION_HOURS = 24;

    /**
     * Playwright BrowserContext의 쿠키를 추출하여 DB + Redis에 저장한다.
     */
    @Transactional
    public String saveSessionFromContext(Long userId, String site, BrowserContext context) {
        List<Cookie> cookieList = context.cookies();
        log.info("[AuthSessionManager] {} 세션 저장 시작 (쿠키 {}개)", site, cookieList.size());

        String cookiesJson = serializeCookies(cookieList);
        saveSessionString(userId, site, cookiesJson);
        return cookiesJson;
    }

    /**
     * 쿠키 문자열을 DB(원본) + Redis(캐시)에 저장한다.
     * 쿠키의 expires 값을 파싱하여 세션 만료 시각도 함께 저장.
     */
    @Transactional
    public void saveSessionString(Long userId, String site, String cookiesJson) {
        SourceSite sourceSite = SourceSite.valueOf(site.toUpperCase());
        Instant expiresAt = estimateSessionExpiry(site);

        // DB 저장 (원본 + 만료시각)
        externalAccountRepository.findByUserIdAndSite(userId, sourceSite)
                .ifPresent(account -> account.updateSessionWithExpiry(cookiesJson, expiresAt));

        // Redis 캐시
        String key = buildSessionKey(userId, site);
        redisTemplate.opsForValue().set(key, cookiesJson, SESSION_EXPIRATION_HOURS, TimeUnit.HOURS);
        log.info("[AuthSessionManager] {} 세션 저장 완료 (userId:{}, 만료:{})", site, userId, expiresAt);
    }

    /**
     * 세션을 가져온다. Redis 캐시 → 없으면 DB에서 복구.
     */
    @Transactional(readOnly = true)
    public String getSession(Long userId, String site) {
        String key = buildSessionKey(userId, site);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        // Redis에 없으면 DB에서 복구
        SourceSite sourceSite = SourceSite.valueOf(site.toUpperCase());
        return externalAccountRepository.findByUserIdAndSite(userId, sourceSite)
                .filter(ExternalAccount::hasValidSession)
                .map(account -> {
                    String cookies = account.getSessionCookies();
                    redisTemplate.opsForValue().set(key, cookies, SESSION_EXPIRATION_HOURS, TimeUnit.HOURS);
                    log.info("[AuthSessionManager] {} 세션 Redis 캐시 복구 (userId:{})", site, userId);
                    return cookies;
                })
                .orElse(null);
    }

    /**
     * 세션이 존재하는지 확인한다. Redis → DB 순서.
     */
    @Transactional(readOnly = true)
    public boolean hasSession(Long userId, String site) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(buildSessionKey(userId, site)))) {
            return true;
        }
        SourceSite sourceSite = SourceSite.valueOf(site.toUpperCase());
        return externalAccountRepository.findByUserIdAndSite(userId, sourceSite)
                .map(ExternalAccount::hasValidSession)
                .orElse(false);
    }

    /**
     * 세션을 삭제한다 (DB + Redis 모두).
     */
    @Transactional
    public void invalidateSession(Long userId, String site) {
        // DB 삭제
        SourceSite sourceSite = SourceSite.valueOf(site.toUpperCase());
        externalAccountRepository.findByUserIdAndSite(userId, sourceSite)
                .ifPresent(ExternalAccount::invalidateSession);

        // Redis 삭제
        String key = buildSessionKey(userId, site);
        redisTemplate.delete(key);
        log.info("[AuthSessionManager] {} 세션 삭제 완료 (userId:{})", site, userId);
    }

    private String buildSessionKey(Long userId, String site) {
        return SESSION_KEY_PREFIX + userId + ":" + site.toUpperCase();
    }

    /**
     * 사이트별 세션 유효 시간 (하드코딩).
     * 각 사이트의 실제 세션 유지 시간을 경험 기반으로 설정.
     */
    private Instant estimateSessionExpiry(String site) {
        long hours = switch (site.toUpperCase()) {
            case "SARAMIN" -> 4;       // 사람인: ~2~4시간
            case "JOBKOREA" -> 12;     // 잡코리아: ~12시간
            case "JOBPLANET" -> 24;    // 잡플래닛: ~24시간
            case "LINKAREER" -> 168;   // 링커리어: ~7일 (OAuth)
            default -> 4;
        };
        return Instant.now().plusSeconds(hours * 3600L);
    }

    private String serializeCookies(List<Cookie> cookieList) {
        try {
            List<Map<String, Object>> cookieData = cookieList.stream().map(c -> {
                Map<String, Object> map = new HashMap<>();
                map.put("name", c.name); map.put("value", c.value);
                map.put("domain", c.domain); map.put("path", c.path);
                map.put("secure", c.secure); map.put("httpOnly", c.httpOnly);
                if (c.expires > 0) map.put("expires", c.expires);
                return map;
            }).collect(Collectors.toList());

            return objectMapper.writeValueAsString(cookieData);
        } catch (Exception ex) {
            log.warn("[AuthSessionManager] 쿠키 JSON 직렬화 실패, 기본 toString() 사용: {}", ex.getMessage());
            return cookieList.toString();
        }
    }
}
