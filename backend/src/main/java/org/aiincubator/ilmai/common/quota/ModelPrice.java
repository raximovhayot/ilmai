package org.aiincubator.ilmai.common.quota;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ModelPrice {

    private BigDecimal promptUsdPerMillion = BigDecimal.ZERO;

    private BigDecimal completionUsdPerMillion = BigDecimal.ZERO;
}
