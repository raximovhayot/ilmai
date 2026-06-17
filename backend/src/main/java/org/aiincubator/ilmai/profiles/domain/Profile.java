package org.aiincubator.ilmai.profiles.domain;

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
import org.aiincubator.ilmai.common.i18n.SupportedLocale;
import org.hibernate.proxy.HibernateProxy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor
@ToString(of = {"userId", "locale", "goal"})
public class Profile extends DateAuditable {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SupportedLocale locale = SupportedLocale.EN;

    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(length = 500)
    private String goal;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "daily_reminder")
    private LocalTime dailyReminder;

    @Column(name = "daily_study_minutes")
    private Integer dailyStudyMinutes;

    @Column(name = "sessions_count", nullable = false)
    private int sessionsCount;

    @Column(name = "quiz_count", nullable = false)
    private int quizCount;

    @Column(name = "streak_days", nullable = false)
    private int streakDays;

    @Column(name = "last_active_at")
    private OffsetDateTime lastActiveAt;

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
        Profile other = (Profile) o;
        return getUserId() != null && Objects.equals(getUserId(), other.getUserId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
