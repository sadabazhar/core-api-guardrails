package com.grid07.coreapi.guardrails.dto.response;

import com.grid07.coreapi.guardrails.entity.AuthorType;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        Long authorId,
        AuthorType authorType,
        String content,
        int depthLevel,
        LocalDateTime createdAt
) {}
