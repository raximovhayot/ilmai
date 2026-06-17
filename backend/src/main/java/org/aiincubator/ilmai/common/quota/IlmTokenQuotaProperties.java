package org.aiincubator.ilmai.common.quota;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "quota")
public class IlmTokenQuotaProperties {

    private int freeDailyIlmTokens = 50;

    private int premiumDailyIlmTokens = 500;

    @NestedConfigurationProperty
    private IlmTokenPricing pricing = new IlmTokenPricing();
}
