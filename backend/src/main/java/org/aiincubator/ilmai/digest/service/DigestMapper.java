package org.aiincubator.ilmai.digest.service;

import org.aiincubator.ilmai.digest.WeeklyDigestDto;
import org.aiincubator.ilmai.digest.domain.WeeklyDigest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DigestMapper {

    WeeklyDigestDto toDto(WeeklyDigest digest);
}
