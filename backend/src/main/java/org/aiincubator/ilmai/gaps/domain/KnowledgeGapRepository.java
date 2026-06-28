package org.aiincubator.ilmai.gaps.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeGapRepository extends JpaRepository<KnowledgeGap, UUID> {

    List<KnowledgeGap> findAllByUserIdOrderByMissCountDescLastSeenAtDesc(UUID userId);

    Optional<KnowledgeGap> findByRoomIdAndConcept(UUID roomId, String concept);
}
