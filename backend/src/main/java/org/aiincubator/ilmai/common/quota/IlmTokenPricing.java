package org.aiincubator.ilmai.common.quota;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IlmTokenPricing {

    private Map<String, Map<String, ModelPrice>> providers = new LinkedHashMap<>();

    private BigDecimal fallbackPromptUsdPerMillion = new BigDecimal("0.15");

    private BigDecimal fallbackCompletionUsdPerMillion = new BigDecimal("0.60");
}
