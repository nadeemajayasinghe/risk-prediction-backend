package com.agilerisk.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CommentRequest(
        String author,
        @NotBlank String body
) { }
