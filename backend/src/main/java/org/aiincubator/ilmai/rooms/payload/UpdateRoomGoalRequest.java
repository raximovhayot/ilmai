package org.aiincubator.ilmai.rooms.payload;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoomGoalRequest {

    @Size(max = 500)
    private String goal;

    private LocalDate targetDate;

    @Positive
    private Integer dailyStudyMinutes;
}
