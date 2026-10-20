package com.company.procurement.modules.ehs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.ehs.dto.EhsAssistantChatDTO;
import com.company.procurement.modules.ehs.dto.EhsKnowledgeSaveDTO;
import com.company.procurement.modules.ehs.entity.EhsAiMessage;
import com.company.procurement.modules.ehs.entity.EhsAiSession;
import com.company.procurement.modules.ehs.entity.EhsKnowledgeDocument;
import com.company.procurement.modules.ehs.entity.EhsRegistryRecord;
import com.company.procurement.modules.ehs.mapper.EhsAiMessageMapper;
import com.company.procurement.modules.ehs.mapper.EhsAiSessionMapper;
import com.company.procurement.modules.ehs.mapper.EhsKnowledgeDocumentMapper;
import com.company.procurement.modules.ehs.vo.EhsAssistantChatVO;
import com.company.procurement.modules.ehs.vo.EhsRegistryStatsVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EhsAssistantService {

    private final EhsKnowledgeDocumentMapper documentMapper;
    private final EhsAiSessionMapper sessionMapper;
    private final EhsAiMessageMapper messageMapper;
    private final EhsRegistryService registryService;
    private final EhsPermitService permitService;
    private final ObjectMapper objectMapper;

    public PageResult<EhsKnowledgeDocument> pageKnowledge(long pageNo, long pageSize, String keyword) {
        LambdaQueryWrapper<EhsKnowledgeDocument> wrapper = new LambdaQueryWrapper<EhsKnowledgeDocument>()
            .eq(EhsKnowledgeDocument::getStatus, "ACTIVE")
            .orderByDesc(EhsKnowledgeDocument::getUpdatedAt)
            .orderByDesc(EhsKnowledgeDocument::getId);
        if (StringUtils.hasText(keyword)) {
            String text = keyword.trim();
            wrapper.and(item -> item.like(EhsKnowledgeDocument::getTitle, text)
                .or().like(EhsKnowledgeDocument::getCategory, text)
                .or().like(EhsKnowledgeDocument::getContent, text));
        }
        Page<EhsKnowledgeDocument> page = documentMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.from(page);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createKnowledge(EhsKnowledgeSaveDTO dto) {
        AuthUser user = AuthContextHolder.requireUser();
        EhsKnowledgeDocument document = new EhsKnowledgeDocument();
        applyKnowledge(document, dto, user);
        documentMapper.insert(document);
        return document.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long uploadKnowledge(String title, String category, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("EHS_KNOWLEDGE_FILE_EMPTY", "请选择知识文件");
        }
        String fileName = file.getOriginalFilename() == null ? "knowledge.txt" : file.getOriginalFilename();
        String ext = extension(fileName);
        if (!Set.of("txt", "md", "csv", "json").contains(ext)) {
            throw new BizException("EHS_KNOWLEDGE_FILE_TYPE", "知识文件支持 txt、md、csv、json");
        }
        try {
            EhsKnowledgeSaveDTO dto = new EhsKnowledgeSaveDTO();
            dto.setTitle(StringUtils.hasText(title) ? title.trim() : stripExtension(fileName));
            dto.setCategory(category);
            dto.setFileName(fileName);
            dto.setFileType(ext.toUpperCase(Locale.ROOT));
            dto.setContent(new String(file.getBytes(), StandardCharsets.UTF_8));
            return createKnowledge(dto);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException("EHS_KNOWLEDGE_FILE_READ", "读取知识文件失败");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledge(Long id, EhsKnowledgeSaveDTO dto) {
        EhsKnowledgeDocument document = requireDocument(id);
        applyKnowledge(document, dto, AuthContextHolder.requireUser());
        documentMapper.updateById(document);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledge(Long id) {
        documentMapper.deleteById(requireDocument(id));
    }

    public List<EhsAiSession> sessions() {
        Long userId = AuthContextHolder.requireUser().getUserId();
        return sessionMapper.selectList(new LambdaQueryWrapper<EhsAiSession>()
            .eq(EhsAiSession::getUserId, userId)
            .eq(EhsAiSession::getStatus, "ACTIVE")
            .orderByDesc(EhsAiSession::getUpdatedAt)
            .orderByDesc(EhsAiSession::getId));
    }

    public List<EhsAiMessage> messages(Long sessionId) {
        requireOwnSession(sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<EhsAiMessage>()
            .eq(EhsAiMessage::getSessionId, sessionId)
            .orderByAsc(EhsAiMessage::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public EhsAssistantChatVO chat(EhsAssistantChatDTO dto) {
        AuthUser user = AuthContextHolder.requireUser();
        EhsAiSession session = dto.getSessionId() == null ? createSession(user, dto.getMessage()) : requireOwnSession(dto.getSessionId());
        saveMessage(session.getId(), "USER", dto.getMessage().trim(), List.of());

        Answer answer = answer(dto.getMessage().trim());
        saveMessage(session.getId(), "ASSISTANT", answer.content(), answer.sources());
        session.setTitle(limit(session.getTitle(), 255));
        sessionMapper.updateById(session);
        return EhsAssistantChatVO.builder()
            .sessionId(session.getId())
            .answer(answer.content())
            .sources(answer.sources())
            .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        sessionMapper.deleteById(requireOwnSession(sessionId));
    }

    private Answer answer(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "审批通过", "替我审批", "自动审批", "批准作业票", "退回作业票")) {
            return new Answer("我不能代替审批人改变审批结果。请在“作业许可”中打开对应作业票，由当前审批人执行通过或退回。", List.of("统一审批规则"));
        }

        List<String> blocks = new ArrayList<>();
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        if (containsAny(normalized, "隐患", "整改", "风险")) {
            blocks.add(statsText("隐患", registryService.stats("HAZARD")));
            sources.add("隐患台账实时统计");
        }
        if (containsAny(normalized, "危废", "废物", "转运", "环境")) {
            blocks.add(statsText("危废", registryService.stats("WASTE")));
            sources.add("危废台账实时统计");
        }
        if (containsAny(normalized, "ppe", "劳保", "应急物资", "库存", "临期")) {
            blocks.add(statsText("PPE与应急物资", registryService.stats("PPE")));
            sources.add("PPE与应急物资台账实时统计");
        }
        if (containsAny(normalized, "事故", "事件", "调查")) {
            blocks.add(statsText("事故事件", registryService.stats("INCIDENT")));
            sources.add("事故事件台账实时统计");
        }
        if (containsAny(normalized, "作业票", "许可", "审批", "动火", "高处", "受限空间", "吊装", "动土", "用电")) {
            Map<String, Long> stats = permitService.stats();
            blocks.add("作业许可：总票 " + stats.getOrDefault("total", 0L)
                + " 张，审批中 " + stats.getOrDefault("approving", 0L)
                + " 张，已批准/执行中 " + stats.getOrDefault("approved", 0L)
                + " 张，已关闭 " + stats.getOrDefault("closed", 0L) + " 张。");
            sources.add("作业许可实时统计");
        }

        List<EhsKnowledgeDocument> documents = searchKnowledge(question);
        if (!documents.isEmpty()) {
            StringBuilder knowledge = new StringBuilder("知识库相关内容：");
            for (EhsKnowledgeDocument document : documents) {
                knowledge.append("\n- ").append(document.getTitle()).append("：")
                    .append(limit(document.getContent().replaceAll("\\s+", " "), 180));
                sources.add("知识库：" + document.getTitle());
            }
            blocks.add(knowledge.toString());
        }

        if (blocks.isEmpty()) {
            EhsRegistryStatsVO hazards = registryService.stats("HAZARD");
            Map<String, Long> permits = permitService.stats();
            blocks.add("当前 EHS 概况：未闭环隐患 " + hazards.getActiveCount()
                + " 项，预警隐患 " + hazards.getAlertCount()
                + " 项，审批中作业许可 " + permits.getOrDefault("approving", 0L)
                + " 张。你可以继续询问隐患、危废、PPE、事故或作业许可。");
            sources.add("EHS实时业务数据");
        }
        return new Answer(String.join("\n\n", blocks), List.copyOf(sources));
    }

    private List<EhsKnowledgeDocument> searchKnowledge(String question) {
        List<String> tokens = List.of(question.trim().split("[\\s,，。；;：:？?]+"));
        List<String> keywords = tokens.stream().filter(token -> token.length() >= 2).limit(5).toList();
        if (keywords.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<EhsKnowledgeDocument> wrapper = new LambdaQueryWrapper<EhsKnowledgeDocument>()
            .eq(EhsKnowledgeDocument::getStatus, "ACTIVE")
            .and(root -> {
                for (int index = 0; index < keywords.size(); index++) {
                    String keyword = keywords.get(index);
                    if (index > 0) {
                        root.or();
                    }
                    root.nested(item -> item.like(EhsKnowledgeDocument::getTitle, keyword)
                        .or().like(EhsKnowledgeDocument::getContent, keyword));
                }
            })
            .orderByDesc(EhsKnowledgeDocument::getUpdatedAt)
            .last("LIMIT 3");
        return documentMapper.selectList(wrapper);
    }

    private String statsText(String name, EhsRegistryStatsVO stats) {
        return name + "：共 " + stats.getTotalCount() + " 条，处理中 " + stats.getActiveCount()
            + " 条，已完成 " + stats.getCompletedCount() + " 条，当前预警 " + stats.getAlertCount() + " 条。";
    }

    private EhsAiSession createSession(AuthUser user, String message) {
        EhsAiSession session = new EhsAiSession();
        session.setUserId(user.getUserId());
        session.setTitle(limit(message.replaceAll("\\s+", " "), 40));
        session.setStatus("ACTIVE");
        sessionMapper.insert(session);
        return session;
    }

    private void saveMessage(Long sessionId, String role, String content, List<String> sources) {
        EhsAiMessage message = new EhsAiMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        try {
            message.setSourceRefsJson(objectMapper.writeValueAsString(sources));
        } catch (Exception ex) {
            message.setSourceRefsJson("[]");
        }
        messageMapper.insert(message);
    }

    private EhsAiSession requireOwnSession(Long id) {
        EhsAiSession session = id == null ? null : sessionMapper.selectById(id);
        Long userId = AuthContextHolder.requireUser().getUserId();
        if (session == null || !userId.equals(session.getUserId())) {
            throw new BizException("EHS_AI_SESSION_NOT_FOUND", "助手会话不存在");
        }
        return session;
    }

    private EhsKnowledgeDocument requireDocument(Long id) {
        EhsKnowledgeDocument document = id == null ? null : documentMapper.selectById(id);
        if (document == null) {
            throw new BizException("EHS_KNOWLEDGE_NOT_FOUND", "知识文档不存在");
        }
        return document;
    }

    private void applyKnowledge(EhsKnowledgeDocument document, EhsKnowledgeSaveDTO dto, AuthUser user) {
        document.setTitle(limit(dto.getTitle(), 255));
        document.setCategory(limit(dto.getCategory(), 128));
        document.setFileName(limit(dto.getFileName(), 255));
        document.setFileType(limit(dto.getFileType(), 32));
        document.setContent(dto.getContent().trim());
        document.setStatus("ACTIVE");
        document.setUploadedBy(user.getUserId());
        document.setUploadedByName(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 1 ? fileName : fileName.substring(0, index);
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private record Answer(String content, List<String> sources) {
    }
}
