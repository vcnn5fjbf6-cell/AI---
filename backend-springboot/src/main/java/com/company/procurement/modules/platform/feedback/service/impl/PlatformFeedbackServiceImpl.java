package com.company.procurement.modules.platform.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.platform.feedback.dto.PlatformFeedbackPageQueryDTO;
import com.company.procurement.modules.platform.feedback.dto.PlatformFeedbackStatusDTO;
import com.company.procurement.modules.platform.feedback.entity.PlatformFeedback;
import com.company.procurement.modules.platform.feedback.entity.PlatformFeedbackAttachment;
import com.company.procurement.modules.platform.feedback.mapper.PlatformFeedbackAttachmentMapper;
import com.company.procurement.modules.platform.feedback.mapper.PlatformFeedbackMapper;
import com.company.procurement.modules.platform.feedback.service.PlatformFeedbackService;
import com.company.procurement.modules.platform.feedback.vo.PlatformFeedbackAttachmentVO;
import com.company.procurement.modules.platform.feedback.vo.PlatformFeedbackVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformFeedbackServiceImpl
    extends ServiceImpl<PlatformFeedbackMapper, PlatformFeedback>
    implements PlatformFeedbackService {

    private static final int MAX_IMAGES = 5;
    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s，。；;]+", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PlatformFeedbackAttachmentMapper attachmentMapper;

    @Value("${platform.feedback.storage-dir:data/feedback}")
    private String storageDir;

    @Override
    @Transactional
    public Long submit(String category, String title, String content, String links, String pageUrl, List<MultipartFile> images) {
        AuthUser user = AuthContextHolder.requireUser();
        List<MultipartFile> validImages = validImages(images);
        String normalizedContent = trim(content);
        String normalizedLinks = mergeLinks(links, normalizedContent);
        if (!StringUtils.hasText(normalizedContent) && !StringUtils.hasText(normalizedLinks) && validImages.isEmpty()) {
            throw new BizException("FEEDBACK_EMPTY", "请填写反馈内容、相关链接或上传图片");
        }

        PlatformFeedback feedback = new PlatformFeedback();
        feedback.setFeedbackNo(nextFeedbackNo());
        feedback.setCategory(normalizeCategory(category));
        feedback.setTitle(limit(firstText(title, defaultTitle(feedback.getCategory())), 200));
        feedback.setContent(limit(normalizedContent, 4000));
        feedback.setLinks(limit(normalizedLinks, 2000));
        feedback.setPageUrl(limit(pageUrl, 1000));
        feedback.setSubmitterUserId(user.getUserId());
        feedback.setSubmitterUsername(limit(user.getUsername(), 64));
        feedback.setSubmitterRealName(limit(user.getRealName(), 128));
        feedback.setSubmitterUserType(limit(user.getUserType(), 32));
        feedback.setSubmitterDeptName(limit(user.getDeptName(), 128));
        feedback.setSubmitterSupplierId(user.getSupplierId());
        feedback.setAttachmentCount(validImages.size());
        feedback.setStatus("OPEN");
        save(feedback);

        for (MultipartFile image : validImages) {
            saveAttachment(feedback.getId(), image);
        }
        return feedback.getId();
    }

    @Override
    public PageResult<PlatformFeedbackVO> page(PlatformFeedbackPageQueryDTO queryDTO) {
        LambdaQueryWrapper<PlatformFeedback> wrapper = new LambdaQueryWrapper<PlatformFeedback>()
            .orderByDesc(PlatformFeedback::getCreatedAt)
            .orderByDesc(PlatformFeedback::getId);
        if (StringUtils.hasText(queryDTO.getCategory())) {
            wrapper.eq(PlatformFeedback::getCategory, normalizeCategory(queryDTO.getCategory()));
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(PlatformFeedback::getStatus, normalizeStatus(queryDTO.getStatus()));
        }
        if (StringUtils.hasText(queryDTO.getKeyword())) {
            String keyword = queryDTO.getKeyword().trim();
            wrapper.and(scope -> scope
                .like(PlatformFeedback::getFeedbackNo, keyword)
                .or()
                .like(PlatformFeedback::getTitle, keyword)
                .or()
                .like(PlatformFeedback::getContent, keyword)
                .or()
                .like(PlatformFeedback::getLinks, keyword)
                .or()
                .like(PlatformFeedback::getSubmitterUsername, keyword)
                .or()
                .like(PlatformFeedback::getSubmitterRealName, keyword)
                .or()
                .like(PlatformFeedback::getSubmitterDeptName, keyword));
        }
        Page<PlatformFeedback> page = page(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::toVOWithoutAttachments).toList());
    }

    @Override
    public PlatformFeedbackVO detail(Long id) {
        PlatformFeedback feedback = getById(id);
        if (feedback == null) {
            throw new BizException("FEEDBACK_NOT_FOUND", "意见反馈不存在");
        }
        PlatformFeedbackVO vo = toVOWithoutAttachments(feedback);
        vo.setAttachments(attachmentMapper.selectList(new LambdaQueryWrapper<PlatformFeedbackAttachment>()
            .eq(PlatformFeedbackAttachment::getFeedbackId, id)
            .orderByAsc(PlatformFeedbackAttachment::getId)
        ).stream().map(this::toAttachmentVO).toList());
        return vo;
    }

    @Override
    public void updateStatus(Long id, PlatformFeedbackStatusDTO dto) {
        PlatformFeedback feedback = getById(id);
        if (feedback == null) {
            throw new BizException("FEEDBACK_NOT_FOUND", "意见反馈不存在");
        }
        AuthUser user = AuthContextHolder.requireUser();
        PlatformFeedback update = new PlatformFeedback();
        update.setId(id);
        update.setStatus(normalizeStatus(dto.getStatus()));
        update.setHandleComment(limit(dto.getHandleComment(), 1000));
        update.setHandlerUserId(user.getUserId());
        update.setHandlerName(limit(firstText(user.getRealName(), user.getUsername()), 128));
        update.setHandledAt(LocalDateTime.now());
        updateById(update);
    }

    @Override
    public FeedbackAttachmentResource loadAttachment(Long attachmentId) {
        PlatformFeedbackAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !StringUtils.hasText(attachment.getStoragePath())) {
            throw new BizException("FEEDBACK_ATTACHMENT_NOT_FOUND", "反馈附件不存在");
        }
        Path root = storageRoot();
        Path file = root.resolve(attachment.getStoragePath()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new BizException("FEEDBACK_ATTACHMENT_NOT_FOUND", "反馈附件文件不存在");
        }
        Resource resource = new FileSystemResource(file);
        long length = 0L;
        try {
            length = Files.size(file);
        } catch (IOException ex) {
            log.debug("Unable to read feedback attachment size: {}", file, ex);
        }
        return new FeedbackAttachmentResource(resource, attachment.getFileName(), attachment.getContentType(), length);
    }

    private List<MultipartFile> validImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<MultipartFile> validImages = images.stream()
            .filter(file -> file != null && !file.isEmpty())
            .toList();
        if (validImages.size() > MAX_IMAGES) {
            throw new BizException("FEEDBACK_IMAGE_LIMIT", "最多上传 5 张图片");
        }
        for (MultipartFile image : validImages) {
            String contentType = image.getContentType();
            if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new BizException("FEEDBACK_IMAGE_TYPE_INVALID", "只能上传图片附件");
            }
            if (image.getSize() > MAX_IMAGE_SIZE) {
                throw new BizException("FEEDBACK_IMAGE_TOO_LARGE", "单张图片不能超过 5MB");
            }
        }
        return validImages;
    }

    private void saveAttachment(Long feedbackId, MultipartFile image) {
        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException ex) {
            throw new BizException("FEEDBACK_IMAGE_READ_FAILED", "读取反馈图片失败");
        }
        String originalName = firstText(image.getOriginalFilename(), "feedback-image");
        String ext = extension(originalName, image.getContentType());
        String relativePath = LocalDate.now().format(NO_DATE) + "/" + UUID.randomUUID() + "." + ext;
        Path root = storageRoot();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new BizException("FEEDBACK_IMAGE_PATH_INVALID", "反馈图片路径不合法");
        }
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = image.getInputStream()) {
                Files.copy(input, target);
            }
        } catch (IOException ex) {
            throw new BizException("FEEDBACK_IMAGE_SAVE_FAILED", "保存反馈图片失败");
        }

        PlatformFeedbackAttachment attachment = new PlatformFeedbackAttachment();
        attachment.setFeedbackId(feedbackId);
        attachment.setFileName(limit(originalName, 255));
        attachment.setFileExt(limit(ext, 32));
        attachment.setContentType(limit(image.getContentType(), 128));
        attachment.setFileSize(image.getSize());
        attachment.setFileSha256(sha256(bytes));
        attachment.setStoragePath(relativePath.replace('\\', '/'));
        attachmentMapper.insert(attachment);
    }

    private PlatformFeedbackVO toVOWithoutAttachments(PlatformFeedback feedback) {
        PlatformFeedbackVO vo = new PlatformFeedbackVO();
        vo.setId(feedback.getId());
        vo.setFeedbackNo(feedback.getFeedbackNo());
        vo.setCategory(feedback.getCategory());
        vo.setTitle(feedback.getTitle());
        vo.setContent(feedback.getContent());
        vo.setLinks(feedback.getLinks());
        vo.setPageUrl(feedback.getPageUrl());
        vo.setSubmitterUserId(feedback.getSubmitterUserId());
        vo.setSubmitterUsername(feedback.getSubmitterUsername());
        vo.setSubmitterRealName(feedback.getSubmitterRealName());
        vo.setSubmitterUserType(feedback.getSubmitterUserType());
        vo.setSubmitterDeptName(feedback.getSubmitterDeptName());
        vo.setSubmitterSupplierId(feedback.getSubmitterSupplierId());
        vo.setAttachmentCount(feedback.getAttachmentCount());
        vo.setStatus(feedback.getStatus());
        vo.setHandlerUserId(feedback.getHandlerUserId());
        vo.setHandlerName(feedback.getHandlerName());
        vo.setHandleComment(feedback.getHandleComment());
        vo.setHandledAt(feedback.getHandledAt());
        vo.setCreatedAt(feedback.getCreatedAt());
        vo.setUpdatedAt(feedback.getUpdatedAt());
        return vo;
    }

    private PlatformFeedbackAttachmentVO toAttachmentVO(PlatformFeedbackAttachment attachment) {
        PlatformFeedbackAttachmentVO vo = new PlatformFeedbackAttachmentVO();
        vo.setId(attachment.getId());
        vo.setFeedbackId(attachment.getFeedbackId());
        vo.setFileName(attachment.getFileName());
        vo.setFileExt(attachment.getFileExt());
        vo.setContentType(attachment.getContentType());
        vo.setFileSize(attachment.getFileSize());
        vo.setFileSha256(attachment.getFileSha256());
        vo.setDownloadUrl("/api/admin/platform/feedbacks/attachments/" + attachment.getId() + "/content");
        vo.setCreatedAt(attachment.getCreatedAt());
        return vo;
    }

    private Path storageRoot() {
        try {
            return Path.of(firstText(storageDir, "data/feedback")).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return Path.of("data/feedback").toAbsolutePath().normalize();
        }
    }

    private String nextFeedbackNo() {
        return "FB"
            + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
            + UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private String normalizeCategory(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BUG", "SUGGESTION", "DATA", "OTHER" -> normalized;
            default -> "SUGGESTION";
        };
    }

    private String normalizeStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OPEN", "PROCESSING", "RESOLVED", "IGNORED" -> normalized;
            default -> throw new BizException("FEEDBACK_STATUS_INVALID", "反馈处理状态不合法");
        };
    }

    private String defaultTitle(String category) {
        return switch (category) {
            case "BUG" -> "问题反馈";
            case "DATA" -> "数据反馈";
            case "OTHER" -> "其他反馈";
            default -> "功能建议";
        };
    }

    private String mergeLinks(String links, String content) {
        List<String> values = new ArrayList<>();
        if (StringUtils.hasText(links)) {
            for (String item : links.split("[\\r\\n,，]+")) {
                if (StringUtils.hasText(item)) {
                    values.add(item.trim());
                }
            }
        }
        if (StringUtils.hasText(content)) {
            Matcher matcher = URL_PATTERN.matcher(content);
            while (matcher.find()) {
                values.add(matcher.group());
            }
        }
        return String.join("\n", values.stream().distinct().toList());
    }

    private String extension(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName;
        int index = name.lastIndexOf('.');
        if (index >= 0 && index < name.length() - 1) {
            String ext = name.substring(index + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (StringUtils.hasText(ext)) {
                return ext;
            }
        }
        if ("image/png".equalsIgnoreCase(contentType)) {
            return "png";
        }
        if ("image/gif".equalsIgnoreCase(contentType)) {
            return "gif";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return "webp";
        }
        return "jpg";
    }

    private String sha256(byte[] content) {
        if (content == null || content.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (Exception ex) {
            return "";
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
