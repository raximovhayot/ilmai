package org.aiincubator.ilmai.billing.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FreeTierQuotas {

    private int dailyQuiz = 3;
    private int dailyChatMessages = 50;
    private int materialUploads = 5;
    private long materialUploadMaxBytesFree = 10L * 1024 * 1024;
    private long materialUploadMaxBytesPremium = 25L * 1024 * 1024;
}
