package org.aiincubator.ilmai.telegram.domain;

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

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "telegram_links")
@Getter
@Setter
@NoArgsConstructor
@ToString(of = {"id", "telegramUsername", "linkedAt"})
public class TelegramLink extends DateAuditable {

    @Id
    @UuidGenerator(algorithm = UuidVersion7Strategy.class)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false, unique = true)
    private UUID userId;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "telegram_username", length = 120)
    private String telegramUsername;

    @Column(name = "link_code", length = 20)
    private String linkCode;

    @Column(name = "link_code_expires_at")
    private OffsetDateTime linkCodeExpiresAt;

    @Column(name = "linked_at")
    private OffsetDateTime linkedAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

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
        TelegramLink other = (TelegramLink) o;
        return getId() != null && Objects.equals(getId(), other.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
