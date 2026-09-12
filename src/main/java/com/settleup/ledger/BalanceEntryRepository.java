package com.settleup.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BalanceEntryRepository extends JpaRepository<BalanceEntry, Long> {

    /**
     * Net position per user, computed by aggregating the append-only ledger rather than reading a
     * stored total. Users whose entries cancel out are still returned, with a balance of zero.
     */
    @Query("""
            select e.userId as userId, sum(e.deltaPaise) as balance
            from BalanceEntry e
            where e.groupId = :groupId
            group by e.userId
            """)
    List<BalanceProjection> findNetBalances(@Param("groupId") Long groupId);

    /** Used by the ledger invariant check: the whole group must net to exactly zero. */
    @Query("select coalesce(sum(e.deltaPaise), 0) from BalanceEntry e where e.groupId = :groupId")
    long sumDeltaByGroupId(@Param("groupId") Long groupId);

    @Query("""
            select coalesce(sum(e.deltaPaise), 0)
            from BalanceEntry e
            where e.groupId = :groupId and e.userId = :userId
            """)
    long sumDeltaByGroupIdAndUserId(@Param("groupId") Long groupId, @Param("userId") Long userId);

    List<BalanceEntry> findByGroupIdOrderByIdAsc(Long groupId);

    interface BalanceProjection {
        Long getUserId();

        Long getBalance();
    }
}
