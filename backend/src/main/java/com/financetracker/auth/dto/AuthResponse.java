package com.financetracker.auth.dto;

import com.financetracker.user.dto.UserResponse;

public record AuthResponse(String token, UserResponse user) {
}
