package org.aiincubator.ilmai.profiles.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OnboardingResponse {

    private String goal;
    private LocalDate targetDate;
    private Integer dailyStudyMinutes;
    private LocalTime dailyReminder;
    private boolean telegramLinked;
    private Boolean onboardingPassed;
}
