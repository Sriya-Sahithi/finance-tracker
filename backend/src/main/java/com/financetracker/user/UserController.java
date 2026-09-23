package com.financetracker.user;

import com.financetracker.common.exception.StandardApiErrors;
import com.financetracker.user.dto.UpdateUserRequest;
import com.financetracker.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users")
@StandardApiErrors
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Current user profile")
    public UserResponse me() {
        return userService.me();
    }

    @PatchMapping("/me")
    @Operation(summary = "Update the current user's name")
    public UserResponse update(@Valid @RequestBody UpdateUserRequest request) {
        return userService.update(request);
    }
}
