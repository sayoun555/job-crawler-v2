package com.portfolio.jobcrawler.application.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskQueue {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    private static final String TASK_PREFIX = "ai:task:";
    private static final Duration TASK_TTL = Duration.ofMinutes(10);

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    public String enqueue(String taskType, Long userId) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        String key = TASK_PREFIX + taskId;

        Map<String, String> task = new ConcurrentHashMap<>();
        task.put("status", PENDING);
        task.put("type", taskType);
        task.put("userId", String.valueOf(userId));
        task.put("result", "");

        redisTemplate.opsForHash().putAll(key, task);
        redisTemplate.expire(key, TASK_TTL);

        log.info("[AI큐] 태스크 등록: {} ({}, userId={})", taskId, taskType, userId);
        return taskId;
    }

    public void complete(String taskId, Long userId, String result) {
        updateRedis(taskId, COMPLETED, result);
        notifyUser(userId, taskId, COMPLETED, result);
        log.info("[AI큐] 태스크 완료: {} ({}자)", taskId, result != null ? result.length() : 0);
    }

    public void fail(String taskId, Long userId, String error) {
        updateRedis(taskId, FAILED, error);
        notifyUser(userId, taskId, FAILED, error);
        log.warn("[AI큐] 태스크 실패: {} ({})", taskId, error);
    }

    public Map<Object, Object> getTask(String taskId) {
        return redisTemplate.opsForHash().entries(TASK_PREFIX + taskId);
    }

    private void updateRedis(String taskId, String status, String result) {
        String key = TASK_PREFIX + taskId;
        redisTemplate.opsForHash().put(key, "status", status);
        if (result != null) {
            redisTemplate.opsForHash().put(key, "result", result);
        }
    }

    private void notifyUser(Long userId, String taskId, String status, String result) {
        try {
            Map<String, Object> message = Map.of(
                    "taskId", taskId,
                    "status", status,
                    "result", result != null ? result : ""
            );
            messagingTemplate.convertAndSend("/topic/ai/" + userId, message);
            log.debug("[WebSocket] 알림 발송: userId={}, taskId={}, status={}", userId, taskId, status);
        } catch (Exception e) {
            log.warn("[WebSocket] 알림 발송 실패: {}", e.getMessage());
        }
    }
}
