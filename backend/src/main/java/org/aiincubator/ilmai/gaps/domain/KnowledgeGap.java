package org.aiincubator.ilmai.gaps.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.aiincubator.ilmai.common.domain.DateAuditable;
import org.aiincubator.ilmai.gaps.GapTrend;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.hibernate.proxy.HibernateProxy;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "knowledge_gaps")
@Getter
@Setter
@NoArgsConstructor
@ToString(of = {"id", "concept", "missCount", "hitCount"})
public class KnowledgeGap extends DateAuditable {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "room_id", nullable = false, updatable = false)
    private UUID roomId;

    @Column(nullable = false, length = 255)
    private String concept;

    @Column(name = "miss_count", nullable = false)
    private int missCount;

    @Column(name = "hit_count", nullable = false)
    private int hitCount;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt = OffsetDateTime.now();

    @Column(name = "suggested_material_id")
    private UUID suggestedMaterialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trend", nullable = false, length = 20)
    private GapTrend trend = GapTrend.FLAT;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        KnowledgeGap other = (KnowledgeGap) o;
        return getId() != null && Objects.equals(getId(), other.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
