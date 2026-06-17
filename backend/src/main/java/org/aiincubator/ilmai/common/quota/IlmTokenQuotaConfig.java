package org.aiincubator.ilmai.common.quota;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IlmTokenQuotaProperties.class)
public class IlmTokenQuotaConfig {
}
