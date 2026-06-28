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

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@ToString(of = {"id", "name"})
public class Room extends DateAuditable {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private boolean personal = false;

    @Column(length = 500)
    private String goal;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "daily_study_minutes")
    private Integer dailyStudyMinutes;

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
        Room other = (Room) o;
        return getId() != null && Objects.equals(getId(), other.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
