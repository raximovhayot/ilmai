package org.aiincubator.ilmai.streaks.service;

import org.aiincubator.ilmai.streaks.StreakDto;
import org.aiincubator.ilmai.streaks.domain.Streak;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StreaksApiMapper {

    StreakDto toDto(Streak streak);
}
