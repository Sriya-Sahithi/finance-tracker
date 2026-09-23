package com.financetracker.category.dto;

import com.financetracker.category.Category;
import com.financetracker.category.CategoryType;
import java.time.Instant;

public record CategoryResponse(Long id, String name, CategoryType type, Instant createdAt) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getType(), category.getCreatedAt());
    }
}
