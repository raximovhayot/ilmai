package org.aiincubator.ilmai.gaps.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.gaps.GapsApi;
import org.aiincubator.ilmai.gaps.GapsReportDto;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DefaultGapsApi implements GapsApi {

    private final GapsService gapsService;
    private final GapsApiMapper gapsApiMapper;

    @Override
    public Optional<GapsReportDto> get(CurrentUser currentUser) {
        try {
            return Optional.of(gapsApiMapper.toDto(gapsService.get(currentUser)));
        } catch (GapsException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<GapsReportDto> get(UUID userId) {
        try {
            return Optional.of(gapsApiMapper.toDto(gapsService.get(userId)));
        } catch (GapsException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<GapsReportDto> refreshAndGet(CurrentUser currentUser) {
        try {
            return Optional.of(gapsApiMapper.toDto(gapsService.refreshAndGet(currentUser)));
        } catch (GapsException ex) {
            return Optional.empty();
        }
    }
}
