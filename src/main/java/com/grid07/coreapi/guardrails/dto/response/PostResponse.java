package com.grid07.coreapi.guardrails.dto.response;

import com.grid07.coreapi.guardrails.entity.AuthorType;
import java.time.LocalDateTime;

public record PostResponse(
        Long id,
        String content,
        Long authorId,
        AuthorType authorType,
        int likeCount,
        LocalDateTime createdAt
) {}
