package org.aiincubator.ilmai.billing.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.billing.payload.CheckoutSessionResponse;
import org.aiincubator.ilmai.billing.payload.PaymentResponse;
import org.aiincubator.ilmai.billing.payload.StartCheckoutRequest;
import org.aiincubator.ilmai.billing.payload.SubscriptionResponse;
import org.aiincubator.ilmai.billing.service.BillingService;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/checkout")
    public ApiResponse<CheckoutSessionResponse> startCheckout(@AuthenticationPrincipal CurrentUser currentUser,
                                                              @Valid @RequestBody StartCheckoutRequest request) {
        return ApiResponse.ok(billingService.startCheckout(currentUser, request.getPlan(), request.getProvider()));
    }

    @GetMapping("/subscription")
    public ApiResponse<SubscriptionResponse> active(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(billingService.activeSubscription(currentUser));
    }

    @GetMapping("/subscriptions")
    public ApiResponse<List<SubscriptionResponse>> history(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(billingService.history(currentUser));
    }

    @GetMapping("/payments")
    public ApiResponse<List<PaymentResponse>> payments(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(billingService.payments(currentUser));
    }

    @DeleteMapping("/subscription")
    public ApiResponse<SubscriptionResponse> cancel(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(billingService.cancel(currentUser));
    }
}
