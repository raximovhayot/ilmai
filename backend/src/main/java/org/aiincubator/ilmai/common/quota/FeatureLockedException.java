package org.aiincubator.ilmai.common.quota;

import lombok.Getter;

@Getter
public class FeatureLockedException extends RuntimeException {

    private final PremiumFeature feature;

    public FeatureLockedException(PremiumFeature feature) {
        super("billing.error.featureLocked");
        this.feature = feature;
    }
}
