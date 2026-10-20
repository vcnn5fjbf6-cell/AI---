package com.company.procurement.modules.ehs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.entity.ApprovalTask;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalTaskMapper;
import com.company.procurement.modules.approval.record.service.ApprovalTaskService;
import com.company.procurement.modules.ehs.entity.EhsKnowledgeDocument;
import com.company.procurement.modules.ehs.entity.EhsNotificationLog;
import com.company.procurement.modules.ehs.entity.EhsRegistryRecord;
import com.company.procurement.modules.ehs.mapper.EhsKnowledgeDocumentMapper;
import com.company.procurement.modules.ehs.mapper.EhsNotificationLogMapper;
import com.company.procurement.modules.ehs.vo.EhsModuleStatusVO;
import com.company.procurement.modules.ehs.vo.EhsOverviewVO;
import com.company.procurement.modules.ehs.vo.EhsRegistryStatsVO;
import com.company.procurement.modules.integration.feishu.config.FeishuApprovalCardRuntimeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EhsOverviewService {

    private static final Set<String> EHS_BIZ_TYPES = Set.of("EHS_WORK_PERMIT", "EHS_SPECIAL_PERMIT");

    private final ApprovalInstanceMapper approvalInstanceMapper;
    private final ApprovalTaskMapper approvalTaskMapper;
    private final EhsRegistryService registryService;
    private final EhsPermitService permitService;
    private final EhsKnowledgeDocumentMapper knowledgeDocumentMapper;
    private final EhsNotificationLogMapper notificationLogMapper;
    private final FeishuApprovalCardRuntimeConfig feishuRuntimeConfig;

    public EhsOverviewVO overview(Long userId) {
        List<ApprovalInstance> instances = approvalInstanceMapper.selectList(
            new LambdaQueryWrapper<ApprovalInstance>()
                .in(ApprovalInstance::getBizType, EHS_BIZ_TYPES)
                .orderByDesc(ApprovalInstance::getUpdatedAt)
        );
        List<Long> instanceIds = instances.stream().map(ApprovalInstance::getId).toList();
        long pendingApprovalCount = userId == null || instanceIds.isEmpty() ? 0L : approvalTaskMapper.selectCount(
            new LambdaQueryWrapper<ApprovalTask>()
                .in(ApprovalTask::getInstanceId, instanceIds)
                .eq(ApprovalTask::getCandidateUserId, userId)
                .eq(ApprovalTask::getStatus, ApprovalTaskService.STATUS_PENDING)
        );
        Map<String, Long> permitStats = permitService.stats();
        List<EhsModuleStatusVO> modules = moduleStatuses();

        return EhsOverviewVO.builder()
            .permitTotalCount(permitStats.getOrDefault("total", 0L))
            .permitApprovingCount(permitStats.getOrDefault("approving", 0L))
            .permitCompletedCount(permitStats.getOrDefault("closed", 0L))
            .myPendingApprovalCount(pendingApprovalCount)
            .knowledgeDocumentCount(knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<EhsKnowledgeDocument>()
                .eq(EhsKnowledgeDocument::getStatus, "ACTIVE")))
            .notificationFailureCount(notificationLogMapper.selectCount(new LambdaQueryWrapper<EhsNotificationLog>()
                .eq(EhsNotificationLog::getStatus, "FAILED")))
            .dataSourceConnected(true)
            .sharedApprovalAvailable(true)
            .sharedNotificationAvailable(feishuRuntimeConfig.current().enabled())
            .refreshedAt(LocalDateTime.now())
            .modules(modules)
            .riskQueue(riskQueue())
            .build();
    }

    private List<EhsModuleStatusVO> moduleStatuses() {
        List<EhsModuleStatusVO> modules = new ArrayList<>();
        modules.add(module("HAZARD", "隐患管理", registryService.stats("HAZARD")));
        modules.add(module("WASTE", "环境与危废", registryService.stats("WASTE")));
        modules.add(module("PPE", "PPE与应急物资", registryService.stats("PPE")));
        modules.add(module("INCIDENT", "事故事件", registryService.stats("INCIDENT")));
        Map<String, Long> permits = permitService.stats();
        modules.add(EhsModuleStatusVO.builder()
            .moduleCode("WORK_PERMIT")
            .moduleName("作业许可")
            .status("CONNECTED")
            .totalCount(permits.getOrDefault("total", 0L))
            .activeCount(permits.getOrDefault("approving", 0L) + permits.getOrDefault("approved", 0L))
            .alertCount(permits.getOrDefault("returned", 0L))
            .build());
        return modules;
    }

    private EhsModuleStatusVO module(String code, String name, EhsRegistryStatsVO stats) {
        return EhsModuleStatusVO.builder()
            .moduleCode(code)
            .moduleName(name)
            .status("CONNECTED")
            .totalCount(stats.getTotalCount())
            .activeCount(stats.getActiveCount())
            .alertCount(stats.getAlertCount())
            .build();
    }

    private List<Map<String, Object>> riskQueue() {
        List<EhsRegistryRecord> records = new ArrayList<>();
        for (String module : List.of("HAZARD", "WASTE", "PPE", "INCIDENT")) {
            records.addAll(registryService.listByModule(module));
        }
        return records.stream()
            .filter(this::isRisk)
            .sorted(Comparator.comparing(EhsRegistryRecord::getDeadlineAt,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(10)
            .map(record -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", record.getId());
                item.put("moduleType", record.getModuleType());
                item.put("recordNo", record.getRecordNo());
                item.put("title", record.getTitle());
                item.put("status", record.getStatus());
                item.put("riskLevel", record.getRiskLevel());
                item.put("deadlineAt", record.getDeadlineAt());
                return item;
            })
            .toList();
    }

    private boolean isRisk(EhsRegistryRecord record) {
        if ("PPE".equals(record.getModuleType())) {
            return Set.of("LOW_STOCK", "EXPIRING").contains(record.getStatus());
        }
        if ("WASTE".equals(record.getModuleType())) {
            return "PENDING_TRANSFER".equals(record.getStatus());
        }
        boolean closed = "CLOSED".equals(record.getStatus());
        return !closed && record.getDeadlineAt() != null
            && !record.getDeadlineAt().isAfter(LocalDateTime.now().plusDays(3));
    }
}
