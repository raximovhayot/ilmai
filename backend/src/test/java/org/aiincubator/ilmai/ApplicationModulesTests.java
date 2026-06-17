package org.aiincubator.ilmai;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTests {

    @Test
    void verifyModules() {
        ApplicationModules.of(IlmaiBackendApplication.class).verify();
    }
}
