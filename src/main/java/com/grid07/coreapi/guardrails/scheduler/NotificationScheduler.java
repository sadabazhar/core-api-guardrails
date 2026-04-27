package com.grid07.coreapi.guardrails.scheduler;

import com.grid07.coreapi.guardrails.common.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final StringRedisTemplate redisTemplate;

    /**
     * Runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void processPendingNotifications() {

        String activeUsersKey = RedisKeys.activeUsers();

        try {
            // Get all users who have pending notifications
            Set<String> userIds = redisTemplate.opsForSet().members(activeUsersKey);

            if (userIds == null || userIds.isEmpty()) {
                log.info("No pending notifications to process");
                return;
            }

            for (String userIdStr : userIds) {

                Long userId = Long.parseLong(userIdStr);
                String listKey = RedisKeys.pendingNotifications(userId);

                // Fetch all pending notifications and delete
                List<String> notifications = new ArrayList<>();
                String msg;
                while ((msg = redisTemplate.opsForList().leftPop(listKey)) != null) {
                    notifications.add(msg);
                }

                if (notifications.isEmpty()) {
                    redisTemplate.opsForSet().remove(activeUsersKey, userIdStr);
                    continue;
                }


                // Create summary
                int total = notifications.size();

                if (total == 1) {
                    log.info("Summarized Push Notification: {}", notifications.get(0));
                } else {
                    String botLabel = extractBotLabel(notifications.get(0));
                    log.info("Summarized Push Notification: {} and {} others interacted with your posts.",
                            botLabel, total - 1);
                }

                redisTemplate.opsForSet().remove(activeUsersKey, userIdStr);
            }

        } catch (Exception ex) {
            log.error("Error processing pending notifications", ex);
        }
    }

    private String extractBotLabel(String message) {
        String[] parts = message.split(" ");
        return parts.length >= 2 ? parts[0] + " " + parts[1] : message;
    }
}
