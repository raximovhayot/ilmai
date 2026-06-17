package org.aiincubator.ilmai.profiles.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileResponse {

    private UUID userId;
    private String locale;
    private String timezone;
    private String goal;
    private LocalDate targetDate;
    private LocalTime dailyReminder;
    private Integer dailyStudyMinutes;
    private int sessionsCount;
    private int quizCount;
    private int streakDays;
    private OffsetDateTime lastActiveAt;
}
