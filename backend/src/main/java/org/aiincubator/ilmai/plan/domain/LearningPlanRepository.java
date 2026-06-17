package org.aiincubator.ilmai.plan.domain;

import org.aiincubator.ilmai.plan.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearningPlanRepository extends JpaRepository<LearningPlan, UUID> {

    List<LearningPlan> findByUserIdAndStatus(UUID userId, PlanStatus status);

    Optional<LearningPlan> findFirstByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, PlanStatus status);

    @Query("select distinct p.userId from LearningPlan p where p.status = :status")
    List<UUID> findDistinctUserIdsByStatus(@Param("status") PlanStatus status);
}
