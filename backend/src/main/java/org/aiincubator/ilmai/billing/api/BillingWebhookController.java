package org.aiincubator.ilmai.billing.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.billing.service.BillingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/billing/webhooks")
@RequiredArgsConstructor
public class BillingWebhookController {

    private final BillingService billingService;

    @PostMapping("/{provider}")
    public ResponseEntity<Void> webhook(@PathVariable("provider") String provider,
                                        @RequestHeader(value = "X-Signature", required = false) String signature,
                                        @RequestBody Map<String, Object> payload) {
        billingService.handleWebhook(provider, payload, signature);
        return ResponseEntity.ok().build();
    }
}
