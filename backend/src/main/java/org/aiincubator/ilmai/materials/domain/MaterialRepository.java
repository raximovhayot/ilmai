package org.aiincubator.ilmai.materials.domain;

import org.aiincubator.ilmai.materials.MaterialStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialRepository extends JpaRepository<Material, UUID> {

    List<Material> findAllByTopicIdAndRoomIdInOrderByCreatedAtDesc(UUID topicId, Collection<UUID> spaceIds);

    List<Material> findAllByRoomIdInOrderByCreatedAtDesc(Collection<UUID> spaceIds);

    Slice<Material> findByTopicIsNullAndRoomIdInOrderByCreatedAtDesc(Collection<UUID> spaceIds, Pageable pageable);

    List<Material> findAllByRoomIdInAndStatusOrderByCreatedAtDesc(Collection<UUID> spaceIds, MaterialStatus status);

    Optional<Material> findByIdAndRoomIdIn(UUID id, Collection<UUID> spaceIds);

    long countByRoomIdInAndStatus(Collection<UUID> spaceIds, MaterialStatus status);

    long countByRoomIdIn(Collection<UUID> spaceIds);

    List<Material> findAllByTopicId(UUID topicId);

    @Modifying
    @Query("update Material m set m.topic = null where m.topic.id = :topicId")
    int detachFromTopic(@Param("topicId") UUID topicId);

    @Query("""
            select m
            from Material m
            where m.status = :status
              and m.retryCount < :maxAttempts
              and m.updatedAt <= :updatedBefore
            order by m.updatedAt asc
            """)
    List<Material> findRetryCandidates(@Param("status") MaterialStatus status,
                                       @Param("maxAttempts") int maxAttempts,
                                       @Param("updatedBefore") OffsetDateTime updatedBefore,
                                       Pageable pageable);
}
