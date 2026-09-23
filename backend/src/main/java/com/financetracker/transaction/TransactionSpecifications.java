package com.financetracker.transaction;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> filter(
            Long userId,
            TransactionType type,
            Long accountId,
            Long categoryId,
            LocalDate from,
            LocalDate to,
            String search
    ) {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("account", JoinType.LEFT);
                root.fetch("category", JoinType.LEFT);
                root.fetch("transferAccount", JoinType.LEFT);
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (accountId != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("account").get("id"), accountId),
                        cb.equal(root.get("transferAccount").get("id"), accountId)));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), to));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase()) + "%";
                Predicate description = cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern, '\\');
                Predicate notes = cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), pattern, '\\');
                predicates.add(cb.or(description, notes));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
