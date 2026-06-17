package org.aiincubator.ilmai.common.quota;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class IlmTokenReservation {

    private final UUID reservationId;
    private final UUID userId;
    private final LocalDate dateLocal;
    private final int estimatedIlmTokens;
}
