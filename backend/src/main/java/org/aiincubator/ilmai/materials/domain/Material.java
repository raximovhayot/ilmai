package org.aiincubator.ilmai.materials.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.aiincubator.ilmai.common.domain.Auditable;
import org.aiincubator.ilmai.materials.MaterialStatus;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "materials")
@Getter
@Setter
@NoArgsConstructor
@ToString(of = {"id", "title", "status"})
public class Material extends Auditable {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID id;

    @Column(name = "room_id", nullable = false, updatable = false)
    private UUID roomId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaterialStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

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
        Material other = (Material) o;
        return getId() != null && Objects.equals(getId(), other.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
