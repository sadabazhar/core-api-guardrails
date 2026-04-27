package com.grid07.coreapi.guardrails.service;

import com.grid07.coreapi.guardrails.common.InteractionType;
import com.grid07.coreapi.guardrails.common.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ViralityService {

    private final StringRedisTemplate redisTemplate;

    private static final Map<InteractionType, Long> POINTS = Map.of(
            InteractionType.BOT_REPLY,     1L,
            InteractionType.HUMAN_LIKE,   20L,
            InteractionType.HUMAN_COMMENT, 50L
    );

    /**
     * Atomically increments the virality score for a post.
     * Returns the new score.
     */
    public Long incrementViralityScore(Long postId, InteractionType interactionType) {

        String key    = RedisKeys.viralityScore(postId);
        Long   points = POINTS.get(interactionType);

        return redisTemplate.opsForValue().increment(key, points);
    }

    /**
     * Fetch current virality score (default = 0).
     */
    public Long getViralityScore(Long postId) {

        String value = redisTemplate.opsForValue().get(RedisKeys.viralityScore(postId));
        return value == null ? 0L : Long.parseLong(value);
    }
}
