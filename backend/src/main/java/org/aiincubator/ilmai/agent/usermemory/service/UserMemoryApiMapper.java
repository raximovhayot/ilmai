package org.aiincubator.ilmai.agent.usermemory.service;

import org.aiincubator.ilmai.agent.ReviewDueDto;
import org.aiincubator.ilmai.agent.UserFactDto;
import org.aiincubator.ilmai.agent.usermemory.domain.ReviewQueueEntry;
import org.aiincubator.ilmai.agent.usermemory.domain.UserMemoryFact;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMemoryApiMapper {

    UserFactDto toDto(UserMemoryFact fact);

    List<UserFactDto> toDtoList(List<UserMemoryFact> facts);

    ReviewDueDto toReviewDto(ReviewQueueEntry entry);

    List<ReviewDueDto> toReviewDtoList(List<ReviewQueueEntry> entries);
}
