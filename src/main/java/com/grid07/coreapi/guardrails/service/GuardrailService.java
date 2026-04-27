package com.grid07.coreapi.guardrails.service;

import com.grid07.coreapi.guardrails.common.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GuardrailService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Horizontal cap: allow max 100 bot replies per post.
     * Uses atomic INCR; rolls back if limit exceeded.
     */
    public boolean tryIncrementBotCount(Long postId) {

        String key = RedisKeys.botCount(postId);
        Long result = redisTemplate.execute(
                botCountScript,
                List.of(key),
                String.valueOf(RedisKeys.MAX_BOT_REPLIES)
        );
        return Long.valueOf(1L).equals(result);
    }

    /**
     * Cooldown: block repeated bot → user interaction within TTL.
     * Uses SET NX (atomic).
     */
    public boolean tryAcquireCooldown(Long botId, Long humanId) {

        String key     = RedisKeys.cooldown(botId, humanId);

        Boolean isNew  = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", RedisKeys.COOLDOWN_TTL_SECONDS, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(isNew);
    }

    /**
     * Vertical cap: limit comment depth.
     */
    public boolean isDepthAllowed(int depthLevel) {
        return depthLevel <= RedisKeys.MAX_DEPTH;
    }

    // Lua Script
    private static final String BOT_COUNT_SCRIPT =
                    "local current = redis.call('GET', KEYS[1]) " +
                    "current = tonumber(current) or 0 " +
                    "if current >= tonumber(ARGV[1]) then return 0 end " +
                    "redis.call('INCR', KEYS[1]) " +
                    "return 1";

    private final RedisScript<Long> botCountScript =
            RedisScript.of(BOT_COUNT_SCRIPT, Long.class);
}