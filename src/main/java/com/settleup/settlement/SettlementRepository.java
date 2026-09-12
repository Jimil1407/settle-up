package com.settleup.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);

    Optional<Settlement> findByIdempotencyKey(String idempotencyKey);
}
