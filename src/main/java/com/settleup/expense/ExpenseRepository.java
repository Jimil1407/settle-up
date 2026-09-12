package com.settleup.expense;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Page<Expense> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId, Pageable pageable);

    List<Expense> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);

    Optional<Expense> findByIdempotencyKey(String idempotencyKey);

    boolean existsByRecurringTemplateIdAndPeriodKey(Long recurringTemplateId, String periodKey);
}
