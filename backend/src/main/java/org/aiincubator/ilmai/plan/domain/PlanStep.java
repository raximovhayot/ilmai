package org.aiincubator.ilmai.plan.domain;

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
import org.aiincubator.ilmai.common.domain.DateAuditable;
import org.aiincubator.ilmai.plan.PlanActivity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "plan_steps")
@Getter
@Setter
@NoArgsConstructor
@ToString(of = {"id", "dayIndex", "activity", "done"})
public class PlanStep extends DateAuditable {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private LearningPlan plan;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    @Column(name = "order_in_day", nullable = false)
    private int orderInDay;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanActivity activity = PlanActivity.READ;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "material_ids", columnDefinition = "jsonb")
    private List<UUID> materialIds;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "reflection_note", columnDefinition = "text")
    private String reflectionNote;

    @Column(name = "quiz_score")
    private Integer quizScore;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "lesson_content", columnDefinition = "text")
    private String lessonContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "lesson_citations", columnDefinition = "jsonb")
    private List<LessonCitation> lessonCitations;

    @Column(name = "lesson_generated_at")
    private OffsetDateTime lessonGeneratedAt;

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
        PlanStep other = (PlanStep) o;
        return getId() != null && Objects.equals(getId(), other.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
