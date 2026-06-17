package org.aiincubator.ilmai.profiles.payload;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
public class UpdateProfileRequest {

    @Size(max = 10)
    private String locale;

    @Size(max = 64)
    private String timezone;

    @Size(max = 500)
    private String goal;

    private LocalDate targetDate;

    private LocalTime dailyReminder;

    @Min(1)
    @Max(1440)
    private Integer dailyStudyMinutes;
}
