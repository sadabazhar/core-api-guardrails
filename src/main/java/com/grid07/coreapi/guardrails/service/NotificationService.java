package com.grid07.coreapi.guardrails.service;

import com.grid07.coreapi.guardrails.common.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Handles bot interaction notification logic.
     *
     * @param userId  Target user (post owner or comment owner)
     * @param message Notification message (e.g., "Bot X replied to your post")
     */
    public void handleBotInteraction(Long userId, String message) {

        String cooldownKey = RedisKeys.notificationCooldown(userId);
        String listKey     = RedisKeys.pendingNotifications(userId);
        String activeUsersKey = RedisKeys.activeUsers();

        try {
            // Try to acquire cooldown (SET NX)
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(
                            cooldownKey,
                            "1",
                            RedisKeys.NOTIFICATION_COOLDOWN_TTL_SECONDS,
                            TimeUnit.SECONDS
                    );

            if (Boolean.TRUE.equals(isNew)) {
                // First interaction, send immediately
                log.info("Push Notification Sent to User {}: {}", userId, message);
                return;
            }

            // If Cooldown exists, batch the notification

            // Push message to list
            redisTemplate.opsForList().rightPush(listKey, message);
            redisTemplate.expire(listKey, 1, TimeUnit.HOURS);

            // Track active users
            redisTemplate.opsForSet().add(activeUsersKey, userId.toString());
            redisTemplate.expire(activeUsersKey, 1, TimeUnit.HOURS);

            log.info("Notification queued for user {}: {}", userId, message);

        } catch (Exception ex) {
            // Notification system should NEVER break main flow
            log.error("Notification handling failed for userId={}", userId, ex);
        }
    }
}
