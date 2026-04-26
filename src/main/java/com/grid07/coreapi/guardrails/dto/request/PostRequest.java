package com.grid07.coreapi.guardrails.dto.request;

import com.grid07.coreapi.guardrails.entity.AuthorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostRequest(
        @NotBlank(message = "Content is required")
        @Size(max = 10000, message = "Content too long")
        String content,

        @NotNull(message = "Author ID is required")
        Long authorId,

        @NotNull(message = "Author type is required")
        AuthorType authorType
) {}
