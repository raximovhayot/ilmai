package org.aiincubator.ilmai.profiles.service;

import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.payload.OnboardingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OnboardingMapper {

    @Mapping(target = "telegramLinked", ignore = true)
    OnboardingResponse toResponse(Profile profile);
}
