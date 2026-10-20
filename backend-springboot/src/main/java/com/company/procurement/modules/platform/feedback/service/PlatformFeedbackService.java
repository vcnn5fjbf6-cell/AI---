package com.company.procurement.modules.platform.feedback.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.platform.feedback.dto.PlatformFeedbackPageQueryDTO;
import com.company.procurement.modules.platform.feedback.dto.PlatformFeedbackStatusDTO;
import com.company.procurement.modules.platform.feedback.vo.PlatformFeedbackVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PlatformFeedbackService {

    Long submit(String category, String title, String content, String links, String pageUrl, List<MultipartFile> images);

    PageResult<PlatformFeedbackVO> page(PlatformFeedbackPageQueryDTO queryDTO);

    PlatformFeedbackVO detail(Long id);

    void updateStatus(Long id, PlatformFeedbackStatusDTO dto);

    FeedbackAttachmentResource loadAttachment(Long attachmentId);

    record FeedbackAttachmentResource(Resource resource, String fileName, String contentType, long contentLength) {
    }
}
