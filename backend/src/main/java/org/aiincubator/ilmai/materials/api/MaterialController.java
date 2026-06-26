package org.aiincubator.ilmai.materials.api;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.common.CurrentUser;
import org.aiincubator.ilmai.common.payload.ApiResponse;
import org.aiincubator.ilmai.materials.payload.MaterialResponse;
import org.aiincubator.ilmai.materials.payload.MoveMaterialRequest;
import org.aiincubator.ilmai.materials.payload.SpaceContentsResponse;
import org.aiincubator.ilmai.materials.service.MaterialService;
import org.aiincubator.ilmai.materials.service.RawMaterial;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MaterialResponse>> upload(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam("spaceId") UUID spaceId,
            @RequestParam(value = "topicId", required = false) UUID topicId,
            @RequestPart("file") MultipartFile file) {
        MaterialResponse response = materialService.upload(currentUser, spaceId, topicId, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ApiResponse<List<MaterialResponse>> list(@AuthenticationPrincipal CurrentUser currentUser,
                                                    @RequestParam(value = "topicId", required = false) UUID topicId) {
        return ApiResponse.ok(materialService.list(currentUser, topicId));
    }

    @GetMapping("/contents")
    public ApiResponse<SpaceContentsResponse> contents(@AuthenticationPrincipal CurrentUser currentUser,
                                                       @RequestParam(value = "page", defaultValue = "0") int page,
                                                       @RequestParam(value = "size", defaultValue = "24") int size) {
        return ApiResponse.ok(materialService.contents(currentUser, page, size));
    }

    @PatchMapping("/{materialId}")
    public ApiResponse<MaterialResponse> move(@AuthenticationPrincipal CurrentUser currentUser,
                                              @PathVariable UUID materialId,
                                              @RequestBody MoveMaterialRequest request) {
        return ApiResponse.ok(materialService.move(currentUser, materialId, request.getTopicId()));
    }

    @GetMapping("/{materialId}")
    public ApiResponse<MaterialResponse> get(@AuthenticationPrincipal CurrentUser currentUser,
                                             @PathVariable UUID materialId) {
        return ApiResponse.ok(materialService.get(currentUser, materialId));
    }

    @GetMapping("/{materialId}/raw")
    public ResponseEntity<ByteArrayResource> raw(@AuthenticationPrincipal CurrentUser currentUser,
                                                 @PathVariable UUID materialId) {
        RawMaterial raw = materialService.openRaw(currentUser, materialId);
        MediaType mediaType = raw.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(raw.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(raw.getContent().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new ByteArrayResource(raw.getContent()));
    }

    @DeleteMapping("/{materialId}")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal CurrentUser currentUser,
                                                    @PathVariable UUID materialId) {
        materialService.delete(currentUser, materialId);
        return ResponseEntity.noContent().build();
    }
}
