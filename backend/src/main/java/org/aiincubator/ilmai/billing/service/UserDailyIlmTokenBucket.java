package org.aiincubator.ilmai.billing.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
final class UserDailyIlmTokenBucket {

    private final LocalDate dateLocal;
    private final int allowance;
    private int reserved;
    private int spent;

    int remaining() {
        return allowance - reserved - spent;
    }

    void reserve(int ilmTokens) {
        this.reserved += ilmTokens;
    }

    void commit(int reservedEstimate, int actual) {
        this.reserved -= reservedEstimate;
        this.spent += actual;
        if (this.reserved < 0) {
            this.reserved = 0;
        }
    }

    void refund(int reservedEstimate) {
        this.reserved -= reservedEstimate;
        if (this.reserved < 0) {
            this.reserved = 0;
        }
    }
}
