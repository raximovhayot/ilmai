package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aiincubator.ilmai.plan.PlanApi;
import org.aiincubator.ilmai.profiles.OnboardingCompletedEvent;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OnboardingPlanInitializer {

    private final PlanBuilder planBuilder;
    private final PlanApi planApi;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOnboardingCompleted(OnboardingCompletedEvent event) {
        UUID userId = event.getUserId();
        if (planApi.getActivePlanForUser(userId).isPresent()) {
            log.debug("onboarding auto-plan skipped (plan exists) user={}", userId);
            return;
        }
        try {
            planBuilder.build(userId, null, null).ifPresentOrElse(
                    plan -> log.info("onboarding auto-plan built user={} steps={}",
                            userId, plan.getSteps().size()),
                    () -> log.debug("onboarding auto-plan not built (insufficient data) user={}", userId));
        } catch (RuntimeException ex) {
            log.warn("onboarding auto-plan failed user={}: {}", userId, ex.toString());
        }
    }
}
