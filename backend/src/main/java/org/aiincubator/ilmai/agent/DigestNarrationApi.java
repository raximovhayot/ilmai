package org.aiincubator.ilmai.agent;

import java.util.Optional;
import java.util.UUID;

public interface DigestNarrationApi {

    Optional<DigestNarration> narrate(UUID userId, DigestNarrationInput input);
}
