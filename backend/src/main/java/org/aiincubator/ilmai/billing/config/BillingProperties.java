package org.aiincubator.ilmai.billing.config;

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
@ConfigurationProperties(prefix = "billing")
public class BillingProperties {

    @NestedConfigurationProperty
    private FreeTierQuotas freeTier = new FreeTierQuotas();
}
