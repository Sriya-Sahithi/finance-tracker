package com.financetracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Schema(example = "ada@example.com")
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "password123")
        @NotBlank @Size(max = 72) String password
) {
}
