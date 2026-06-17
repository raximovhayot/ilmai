package org.aiincubator.ilmai.telegram.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.telegram.payload.TelegramLinkResponse;
import org.aiincubator.ilmai.telegram.service.TelegramService;
import org.aiincubator.ilmai.telegram.service.TelegramUpdateHandler;
import org.aiincubator.ilmai.telegram.service.TelegramUpdateParser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

@RestController
@RequestMapping("/telegram")
@RequiredArgsConstructor
public class TelegramController {

    private final TelegramService telegramService;
    private final TelegramUpdateHandler telegramUpdateHandler;
    private final TelegramUpdateParser telegramUpdateParser;

    @PostMapping("/link-code")
    public ApiResponse<TelegramLinkResponse> createLinkCode(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(telegramService.createLinkCode(currentUser));
    }

    @GetMapping
    public ApiResponse<TelegramLinkResponse> get(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(telegramService.getLink(currentUser));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> unlink(@AuthenticationPrincipal CurrentUser currentUser) {
        telegramService.unlink(currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/webhook/{secret}")
    public ResponseEntity<ApiResponse<Void>> webhook(@PathVariable("secret") String secret,
                                                     @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String headerSecret,
                                                     @RequestBody byte[] body) {
        Update update = telegramUpdateParser.parse(body);
        telegramUpdateHandler.handleWebhook(secret, headerSecret, update);
        return ResponseEntity.ok().build();
    }
}
