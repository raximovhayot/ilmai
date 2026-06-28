package org.aiincubator.ilmai.profiles.payload;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    private LocalTime dailyReminder;
}
