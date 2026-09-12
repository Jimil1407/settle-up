package com.settleup.recurring;

import com.settleup.expense.SplitType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A standing expense such as rent or a shared Netflix plan, materialised monthly by a job. */
@Entity
@Table(name = "recurring_expense_template")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringExpenseTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "description", nullable = false, length = 200)
    private String description;

    @Column(name = "total_paise", nullable = false)
    private long totalPaise;

    @Column(name = "paid_by_user_id", nullable = false)
    private Long paidByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    /** Day of month to charge on; clamped to the last day for short months. */
    @Column(name = "day_of_month", nullable = false)
    private int dayOfMonth;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
