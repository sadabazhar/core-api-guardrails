package com.grid07.coreapi.guardrails.common;

public final class RedisKeys {

    // Prevent Object creation
    private RedisKeys() {}


    // Prefixes
    private static final String POST = "post";
    private static final String COOLDOWN = "cooldown";


    // Keys
    public static String viralityScore(Long postId) {
        return POST + ":" + postId + ":virality_score";
    }

    public static String botCount(Long postId) {
        return POST + ":" + postId + ":bot_count";
    }

    public static String cooldown(Long botId, Long humanId) {
        return COOLDOWN + ":bot:" + botId + ":human:" + humanId;
    }


    // Constants
    public static final int MAX_BOT_REPLIES = 100;
    public static final long COOLDOWN_TTL_SECONDS = 600;
    public static final long MAX_DEPTH = 20;
}
