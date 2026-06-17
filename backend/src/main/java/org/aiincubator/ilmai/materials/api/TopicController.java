package org.aiincubator.ilmai.materials.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.materials.payload.CreateTopicRequest;
import org.aiincubator.ilmai.materials.payload.RenameTopicRequest;
import org.aiincubator.ilmai.materials.payload.TopicResponse;
import org.aiincubator.ilmai.materials.service.TopicService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @PostMapping
    public ApiResponse<TopicResponse> create(@AuthenticationPrincipal CurrentUser currentUser,
                                             @Valid @RequestBody CreateTopicRequest request) {
        return ApiResponse.ok(topicService.create(currentUser, request.getName()));
    }

    @GetMapping
    public ApiResponse<List<TopicResponse>> list(@AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(topicService.list(currentUser));
    }

    @PatchMapping("/{topicId}")
    public ApiResponse<TopicResponse> rename(@AuthenticationPrincipal CurrentUser currentUser,
                                             @PathVariable UUID topicId,
                                             @Valid @RequestBody RenameTopicRequest request) {
        return ApiResponse.ok(topicService.rename(currentUser, topicId, request.getName()));
    }

    @DeleteMapping("/{topicId}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal CurrentUser currentUser,
                                                    @PathVariable UUID topicId,
                                                    @RequestParam(value = "deleteMaterials", defaultValue = "false") boolean deleteMaterials) {
        topicService.delete(currentUser, topicId, deleteMaterials);
        return ResponseEntity.noContent().build();
    }
}
