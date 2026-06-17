package org.aiincubator.ilmai.digest.domain;

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
import org.aiincubator.ilmai.digest.DigestVariant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "weekly_digests")
@Getter
@Setter
@NoArgsConstructor
@ToString(of = {"id", "userId", "isoWeek", "variant"})
public class WeeklyDigest extends DateAuditable {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "iso_week", nullable = false, length = 10)
    private String isoWeek;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DigestVariant variant;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "active_days", nullable = false)
    private int activeDays;

    @Column(nullable = false)
    private int quizzes;

    @Column(nullable = false)
    private int answered;

    @Column(nullable = false)
    private int correct;

    @Column(name = "avg_score")
    private Integer avgScore;

    @Column(name = "plan_done", nullable = false)
    private int planDone;

    @Column(name = "plan_total", nullable = false)
    private int planTotal;

    @Column(name = "streak_now", nullable = false)
    private int streakNow;

    @Column(name = "days_until_deadline")
    private Integer daysUntilDeadline;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "top_gaps", columnDefinition = "jsonb")
    private List<String> topGaps;

    @Column(name = "where_you_stand", columnDefinition = "text")
    private String whereYouStand;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "focus_next_week", columnDefinition = "jsonb")
    private List<String> focusNextWeek;

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
        WeeklyDigest other = (WeeklyDigest) o;
        return getId() != null && Objects.equals(getId(), other.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
