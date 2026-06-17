package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.quota.IlmTokenPricing;
import org.aiincubator.ilmai.common.quota.IlmTokenQuotaProperties;
import org.aiincubator.ilmai.common.quota.ModelPrice;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class IlmTokenCostCalculator {

    static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    static final BigDecimal HUNDRED = new BigDecimal("100");

    private final IlmTokenQuotaProperties quotaProperties;

    public int costInIlmTokens(String provider, String model, long promptTokens, long completionTokens) {
        ModelPrice price = lookup(provider, model);
        BigDecimal promptUsd = BigDecimal.valueOf(Math.max(promptTokens, 0L))
                .multiply(price.getPromptUsdPerMillion())
                .divide(ONE_MILLION, 10, RoundingMode.HALF_UP);
        BigDecimal completionUsd = BigDecimal.valueOf(Math.max(completionTokens, 0L))
                .multiply(price.getCompletionUsdPerMillion())
                .divide(ONE_MILLION, 10, RoundingMode.HALF_UP);
        BigDecimal totalUsd = promptUsd.add(completionUsd);
        BigDecimal ilm = totalUsd.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP);
        return ilm.intValueExact();
    }

    private ModelPrice lookup(String provider, String model) {
        IlmTokenPricing pricing = quotaProperties.getPricing();
        Map<String, Map<String, ModelPrice>> providers = pricing.getProviders();
        if (providers != null && provider != null) {
            Map<String, ModelPrice> models = providers.get(provider);
            if (models != null && model != null) {
                ModelPrice exact = models.get(model);
                if (exact != null) {
                    return exact;
                }
            }
        }
        ModelPrice fallback = new ModelPrice();
        fallback.setPromptUsdPerMillion(pricing.getFallbackPromptUsdPerMillion());
        fallback.setCompletionUsdPerMillion(pricing.getFallbackCompletionUsdPerMillion());
        return fallback;
    }
}
