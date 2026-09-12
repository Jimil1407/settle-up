package com.settleup.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {

    List<GroupMember> findByGroupId(Long groupId);

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    @Query("select m.userId from GroupMember m where m.groupId = :groupId")
    List<Long> findUserIdsByGroupId(@Param("groupId") Long groupId);

    @Query("select m.groupId from GroupMember m where m.userId = :userId")
    List<Long> findGroupIdsByUserId(@Param("userId") Long userId);

    long countByGroupId(Long groupId);
}
