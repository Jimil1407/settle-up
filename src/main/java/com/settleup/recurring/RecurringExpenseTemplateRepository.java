package com.settleup.recurring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecurringExpenseTemplateRepository extends JpaRepository<RecurringExpenseTemplate, Long> {

    List<RecurringExpenseTemplate> findByActiveTrue();

    List<RecurringExpenseTemplate> findByGroupId(Long groupId);
}
