package com.portfolio.jobcrawler.infrastructure.autoapply;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Cookie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 사용자별 외부 계정 세션(쿠키)을 Redis에 관리하는 책임을 가진다. (SRP 준수)
 * "AutoApplyRobot"의 무거운 캐시 로직을 분리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSessionManager {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final int SESSION_EXPIRATION_HOURS = 24;

    /**
     * Playwright BrowserContext의 쿠키를 추출하여 Redis에 저장한다.
     */
    public String saveSessionFromContext(Long userId, String site, BrowserContext context) {
        List<Cookie> cookieList = context.cookies();
        log.info("[AuthSessionManager] {} 세션 저장 시작 (쿠키 {}개)", site, cookieList.size());

        String cookiesJson = serializeCookies(cookieList);
        saveSessionString(userId, site, cookiesJson);
        return cookiesJson;
    }

    /**
     * 이미 직렬화된 쿠키 문자열을 Redis에 저장한다.
     */
    public void saveSessionString(Long userId, String site, String cookiesJson) {
        String key = buildSessionKey(userId, site);
        redisTemplate.opsForValue().set(key, cookiesJson, SESSION_EXPIRATION_HOURS, TimeUnit.HOURS);
        log.info("[AuthSessionManager] {} 세션 Redis 저장 완료 (userId:{})", site, userId);
    }

    /**
     * Redis에서 저장된 세션(쿠키) 문자열을 가져온다.
     */
    public String getSession(Long userId, String site) {
        return redisTemplate.opsForValue().get(buildSessionKey(userId, site));
    }

    /**
     * Redis에 세션이 존재하는지 확인한다.
     */
    public boolean hasSession(Long userId, String site) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildSessionKey(userId, site)));
    }

    private String buildSessionKey(Long userId, String site) {
        return SESSION_KEY_PREFIX + userId + ":" + site.toUpperCase();
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
