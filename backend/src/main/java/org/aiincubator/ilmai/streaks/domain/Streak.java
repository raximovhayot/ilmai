package org.aiincubator.ilmai.streaks.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.aiincubator.ilmai.common.domain.DateAuditable;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "streaks")
@Getter
@Setter
@NoArgsConstructor
@ToString(of = {"userId", "streakCurrent", "streakLongest", "streakLastDay"})
public class Streak extends DateAuditable {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "streak_current", nullable = false)
    private int streakCurrent;

    @Column(name = "streak_longest", nullable = false)
    private int streakLongest;

    @Column(name = "streak_last_day")
    private LocalDate streakLastDay;

    @Column(name = "streak_broken_at")
    private LocalDate streakBrokenAt;

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
        Streak other = (Streak) o;
        return getUserId() != null && Objects.equals(getUserId(), other.getUserId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
