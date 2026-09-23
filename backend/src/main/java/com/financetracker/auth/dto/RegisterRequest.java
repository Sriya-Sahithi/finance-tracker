package com.financetracker.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Schema(example = "ada@example.com")
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "password123", minLength = 8, maxLength = 72)
        @NotBlank @Size(min = 8, max = 72) String password,
        @Schema(example = "Ada Lovelace")
        @NotBlank @Size(max = 120) String name
) {
}
