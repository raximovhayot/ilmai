package org.aiincubator.ilmai.agent.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.agent.service.PlanBuilder;
import org.aiincubator.ilmai.common.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PlanGenerationController {

    private final PlanBuilder planBuilder;

    @PostMapping("/plan/generate")
    public ResponseEntity<Void> generate(@AuthenticationPrincipal CurrentUser currentUser) {
        planBuilder.build(currentUser.getUserId(), null, null);
        return ResponseEntity.noContent().build();
    }
}
