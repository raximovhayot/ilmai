package org.aiincubator.ilmai.materials.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "ingestion.retry")
public class MaterialReingestProperties {

    private boolean enabled = true;
    private int maxAttempts = 3;
    private Duration minFailureAge = Duration.ofMinutes(5);
    private int batchSize = 20;
}
