package org.aiincubator.ilmai.profiles.service;

import org.aiincubator.ilmai.profiles.ProfileDto;
import org.aiincubator.ilmai.profiles.domain.Profile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProfilesApiMapper {

    ProfileDto toProfileDto(Profile profile);
}
