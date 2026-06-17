package org.aiincubator.ilmai.gaps;

import org.aiincubator.ilmai.common.CurrentUser;

import java.util.Optional;
import java.util.UUID;

public interface GapsApi {

    Optional<GapsReportDto> get(CurrentUser currentUser);

    Optional<GapsReportDto> get(UUID userId);

    Optional<GapsReportDto> refreshAndGet(CurrentUser currentUser);
}
