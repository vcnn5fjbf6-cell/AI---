package com.company.procurement.modules.procurement.assistant.controller;

import com.company.procurement.modules.procurement.assistant.service.ProcurementAiImageStoreService;
import com.company.procurement.modules.procurement.assistant.service.ProcurementAiImageStoreService.ImageResource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/procurement/ai/images")
@RequiredArgsConstructor
public class ProcurementAiImageController {

    private final ProcurementAiImageStoreService imageStoreService;

    @GetMapping("/{token}")
    public ResponseEntity<byte[]> image(@PathVariable String token) {
        ImageResource resource = imageStoreService.load(token);
        if (resource == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(resource.mediaType())
            .contentLength(resource.bytes().length)
            .body(resource.bytes());
    }
}
