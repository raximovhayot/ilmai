package org.aiincubator.ilmai.gaps.service;

import org.aiincubator.ilmai.gaps.GapItemDto;
import org.aiincubator.ilmai.gaps.GapsReportDto;
import org.aiincubator.ilmai.gaps.payload.GapItem;
import org.aiincubator.ilmai.gaps.payload.GapsReportResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GapsApiMapper {

    GapItemDto toDto(GapItem item);

    List<GapItemDto> toDtoList(List<GapItem> items);

    GapsReportDto toDto(GapsReportResponse response);
}
