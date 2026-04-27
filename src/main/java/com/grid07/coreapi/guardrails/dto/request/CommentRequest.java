package com.grid07.coreapi.guardrails.dto.request;

import com.grid07.coreapi.guardrails.entity.AuthorType;
import jakarta.validation.constraints.*;

public record CommentRequest(

        @NotNull(message = "Author ID is required")
        Long authorId,

        @NotNull(message = "Author type is required")
        AuthorType authorType,

        @NotBlank(message = "Content is required")
        @Size(max = 5000, message = "Content too long")
        String content,

        // null represent top-level comment
        Long parentCommentId
) {}