package org.aiincubator.ilmai.digest;

import java.util.Optional;
import java.util.UUID;

public interface DigestApi {

    Optional<WeeklyDigestDto> getLatestForUser(UUID userId);
}
