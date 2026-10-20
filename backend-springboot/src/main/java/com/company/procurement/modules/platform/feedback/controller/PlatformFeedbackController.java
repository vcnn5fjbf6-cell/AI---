package com.company.procurement.modules.platform.feedback.controller;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.result.Result;
import com.company.procurement.modules.platform.feedback.dto.PlatformFeedbackPageQueryDTO;
import com.company.procurement.modules.platform.feedback.dto.PlatformFeedbackStatusDTO;
import com.company.procurement.modules.platform.feedback.service.PlatformFeedbackService;
import com.company.procurement.modules.platform.feedback.vo.PlatformFeedbackVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PlatformFeedbackController {

    private final PlatformFeedbackService feedbackService;

    @PostMapping(value = "/api/platform/feedbacks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Long> submit(
        @RequestParam(value = "category", required = false) String category,
        @RequestParam(value = "title", required = false) String title,
        @RequestParam(value = "content", required = false) String content,
        @RequestParam(value = "links", required = false) String links,
        @RequestParam(value = "pageUrl", required = false) String pageUrl,
        @RequestParam(value = "images", required = false) List<MultipartFile> images
    ) {
        return Result.ok(feedbackService.submit(category, title, content, links, pageUrl, images));
    }

    @GetMapping("/api/admin/platform/feedbacks/page")
    public Result<PageResult<PlatformFeedbackVO>> page(@Valid PlatformFeedbackPageQueryDTO queryDTO) {
        return Result.ok(feedbackService.page(queryDTO));
    }

    @GetMapping("/api/admin/platform/feedbacks/{id:\\d+}")
    public Result<PlatformFeedbackVO> detail(@PathVariable Long id) {
        return Result.ok(feedbackService.detail(id));
    }

    @PutMapping("/api/admin/platform/feedbacks/{id:\\d+}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid PlatformFeedbackStatusDTO dto) {
        feedbackService.updateStatus(id, dto);
        return Result.ok();
    }

    @GetMapping("/api/admin/platform/feedbacks/attachments/{id:\\d+}/content")
    public ResponseEntity<?> attachment(@PathVariable Long id) {
        PlatformFeedbackService.FeedbackAttachmentResource attachment = feedbackService.loadAttachment(id);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (attachment.contentType() != null) {
            try {
                mediaType = MediaType.parseMediaType(attachment.contentType());
            } catch (RuntimeException ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }
        }
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(attachment.contentLength())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(attachment.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .body(attachment.resource());
    }
}
