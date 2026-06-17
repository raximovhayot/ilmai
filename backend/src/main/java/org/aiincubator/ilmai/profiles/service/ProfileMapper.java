package org.aiincubator.ilmai.profiles.service;

import org.aiincubator.ilmai.profiles.domain.Profile;
import org.aiincubator.ilmai.profiles.payload.ProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProfileMapper {

    @Mapping(target = "locale", expression = "java(profile.getLocale() != null ? profile.getLocale().name() : null)")
    ProfileResponse toResponse(Profile profile);
}
