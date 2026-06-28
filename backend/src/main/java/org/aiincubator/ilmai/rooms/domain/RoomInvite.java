package org.aiincubator.ilmai.rooms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.aiincubator.ilmai.common.domain.DateAuditable;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "room_invites")
@Getter
@Setter
@NoArgsConstructor
@ToString(of = {"id", "roomId", "revoked"})
public class RoomInvite extends DateAuditable {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID id;

    @Column(name = "room_id", nullable = false, updatable = false)
    private UUID roomId;

    @Column(nullable = false, length = 64, updatable = false)
    private String code;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private boolean revoked = false;

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
        RoomInvite other = (RoomInvite) o;
        return getId() != null && Objects.equals(getId(), other.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
