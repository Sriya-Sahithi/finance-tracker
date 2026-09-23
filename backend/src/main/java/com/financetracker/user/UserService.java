package com.financetracker.user;

import com.financetracker.common.security.CurrentUserService;
import com.financetracker.user.dto.UpdateUserRequest;
import com.financetracker.user.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final CurrentUserService currentUserService;

    public UserService(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return UserResponse.from(currentUserService.require());
    }

    @Transactional
    public UserResponse update(UpdateUserRequest request) {
        User user = currentUserService.require();
        user.setName(request.name().trim());
        return UserResponse.from(user);
    }
}
