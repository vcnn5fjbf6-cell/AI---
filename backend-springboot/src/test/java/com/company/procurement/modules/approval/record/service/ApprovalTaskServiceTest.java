package com.company.procurement.modules.approval.record.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.entity.ApprovalTask;
import com.company.procurement.modules.approval.record.event.ApprovalTasksCreatedEvent;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalTaskMapper;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.workflow.ProcurementWorkflow;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.procurement.settings.entity.ProcurementSetting;
import com.company.procurement.modules.procurement.settings.mapper.ProcurementSettingMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalTaskServiceTest {

    @Mock
    private ApprovalTaskMapper approvalTaskMapper;
    @Mock
    private ApprovalInstanceMapper approvalInstanceMapper;
    @Mock
    private ProcurementSettingMapper settingMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ApprovalTaskService service;

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ApprovalTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ProcurementSetting.class);
    }

    @BeforeEach
    void setUp() {
        service = new ApprovalTaskService(approvalTaskMapper, approvalInstanceMapper, settingMapper, eventPublisher);
    }

    @Test
    void createTasksPublishesCardScanEvent() {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(10L);
        PurchaseRequest request = new PurchaseRequest();
        request.setId(20L);
        request.setSubmitCount(1);
        request.setReqNo("PTCG-100001");
        ProcurementApprovalRouteResolver.ApprovalAssignment assignment =
            new ProcurementApprovalRouteResolver.ApprovalAssignment(
                30L,
                List.of(30L),
                "审批人",
                true,
                null,
                null,
                1,
                "FLOOR_LEADER",
                "楼长审核",
                ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE,
                true
            );
        when(approvalTaskMapper.selectOne(any())).thenReturn(null);

        service.createTasks(instance, request, assignment);

        verify(approvalTaskMapper).insert(org.mockito.ArgumentMatchers.<ApprovalTask>argThat(task ->
            task instanceof ApprovalTask
                && task.getPurchaseRequestId().equals(20L)
                && task.getCandidateUserId().equals(30L)
                && ApprovalTaskService.STATUS_PENDING.equals(task.getStatus())
        ));
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
            .isInstanceOf(ApprovalTasksCreatedEvent.class)
            .extracting("purchaseRequestId")
            .isEqualTo(20L);
    }

    @Test
    void replacementConfirmationTaskDueAtIsTwoDaysLater() {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(11L);
        PurchaseRequest request = new PurchaseRequest();
        request.setId(21L);
        request.setSubmitCount(1);
        request.setReqNo("HZCG-100001");
        ProcurementApprovalRouteResolver.ApprovalAssignment assignment =
            new ProcurementApprovalRouteResolver.ApprovalAssignment(
                31L,
                List.of(31L),
                "申请人",
                true,
                null,
                null,
                10,
                "APPLICANT_REPLACEMENT_CONFIRM",
                "申请人替换商品确认",
                ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE,
                true
            );
        when(approvalTaskMapper.selectOne(any())).thenReturn(null);

        LocalDateTime before = LocalDateTime.now();
        service.createTasks(instance, request, assignment);
        LocalDateTime after = LocalDateTime.now();

        verify(approvalTaskMapper).insert(org.mockito.ArgumentMatchers.<ApprovalTask>argThat(task ->
            task.getDueAt() != null
                && !task.getDueAt().isBefore(before.plusDays(2))
                && !task.getDueAt().isAfter(after.plusDays(2))
        ));
    }

    @Test
    void overPriceReviewTaskIsDueExactlyTwentyFourHoursLater() {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(15L);
        PurchaseRequest request = new PurchaseRequest();
        request.setId(25L);
        request.setSubmitCount(1);
        request.setReqNo("PTCG-100002");
        ProcurementApprovalRouteResolver.ApprovalAssignment assignment =
            new ProcurementApprovalRouteResolver.ApprovalAssignment(
                35L,
                List.of(35L),
                "楼长",
                true,
                null,
                null,
                3,
                ProcurementWorkflow.LEVEL_CODE_OVER_PRICE_REVIEW,
                "楼长超价复核",
                ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE,
                true
            );
        when(approvalTaskMapper.selectOne(any())).thenReturn(null);

        LocalDateTime before = LocalDateTime.now();
        service.createTasks(instance, request, assignment);
        LocalDateTime after = LocalDateTime.now();

        verify(approvalTaskMapper).insert(org.mockito.ArgumentMatchers.<ApprovalTask>argThat(task ->
            task.getDueAt() != null
                && !task.getDueAt().isBefore(before.plusHours(24))
                && !task.getDueAt().isAfter(after.plusHours(24))
                && Integer.valueOf(0).equals(task.getReminderStage())
        ));
    }

    @Test
    void createBusinessTaskUsesSharedTaskTableWithoutPurchaseRequest() {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(12L);
        when(approvalTaskMapper.selectOne(any())).thenReturn(null);

        service.createBusinessTask(
            instance,
            32L,
            1,
            "EHS_LEVEL_1",
            "EHS第1级审批",
            "EHS_APPROVER",
            1,
            LocalDateTime.now().plusDays(2)
        );

        verify(approvalTaskMapper).insert(org.mockito.ArgumentMatchers.<ApprovalTask>argThat(task ->
            task.getPurchaseRequestId() == null
                && task.getInstanceId().equals(12L)
                && task.getCandidateUserId().equals(32L)
                && "EHS_LEVEL_1".equals(task.getLevelCode())
                && ApprovalTaskService.STATUS_PENDING.equals(task.getStatus())
        ));
    }

    @Test
    void createBusinessTasksKeepsDepartmentCandidatesAndPassRule() {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(13L);
        ProcurementApprovalRouteResolver.ApprovalAssignment assignment =
            new ProcurementApprovalRouteResolver.ApprovalAssignment(
                null,
                List.of(32L, 33L),
                "审批人甲,审批人乙",
                true,
                1L,
                2L,
                3,
                "V3",
                "楼长审核",
                ProcurementApprovalRouteResolver.PASS_RULE_ALL,
                true
            );
        when(approvalTaskMapper.selectOne(any())).thenReturn(null);

        service.createBusinessTasks(instance, assignment, "FLOOR_LEADER", 1, LocalDateTime.now().plusDays(4));

        verify(approvalTaskMapper, org.mockito.Mockito.times(2)).insert(
            org.mockito.ArgumentMatchers.<ApprovalTask>argThat(task ->
                task.getPurchaseRequestId() == null
                    && task.getInstanceId().equals(13L)
                    && List.of(32L, 33L).contains(task.getCandidateUserId())
                    && ProcurementApprovalRouteResolver.PASS_RULE_ALL.equals(task.getPassRule())
                    && task.getLevelNo().equals(3)
            )
        );
    }

    @Test
    void completeBusinessTaskWaitsForEveryCandidateWhenDepartmentRuleIsAll() {
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(14L);
        instance.setCurrentLevelNo(3);
        instance.setCurrentPassRule(ProcurementApprovalRouteResolver.PASS_RULE_ALL);
        ApprovalTask current = pendingTask(14L, 3, 32L, ProcurementApprovalRouteResolver.PASS_RULE_ALL);
        ApprovalTask peer = pendingTask(14L, 3, 33L, ProcurementApprovalRouteResolver.PASS_RULE_ALL);
        when(approvalTaskMapper.selectList(any())).thenReturn(List.of(current, peer), List.of(peer));

        boolean completed = service.completeBusinessTask(
            instance,
            AuthUser.builder().userId(32L).roleCodes(List.of("FLOOR_LEADER")).build(),
            "APPROVE",
            "同意",
            LocalDateTime.now()
        );

        assertThat(completed).isFalse();
        assertThat(current.getStatus()).isEqualTo(ApprovalTaskService.STATUS_APPROVED);
        assertThat(peer.getStatus()).isEqualTo(ApprovalTaskService.STATUS_PENDING);
    }

    @Test
    void reassignPendingTaskUpdatesCandidateCurrentApproverAndPublishesEvent() {
        ApprovalTask task = new ApprovalTask();
        task.setId(100L);
        task.setInstanceId(10L);
        task.setPurchaseRequestId(20L);
        task.setLevelNo(4);
        task.setSubmitRound(1);
        task.setCandidateUserId(30L);
        task.setStatus(ApprovalTaskService.STATUS_PENDING);
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(10L);
        instance.setCurrentApproverId(30L);
        when(approvalTaskMapper.selectById(100L)).thenReturn(task);
        when(approvalTaskMapper.selectOne(any())).thenReturn(null);
        when(approvalInstanceMapper.selectById(10L)).thenReturn(instance);

        service.reassignPendingTasks(List.of(
            new ApprovalTaskService.PendingTaskReassignment(100L, 30L, 31L)
        ));

        assertThat(task.getCandidateUserId()).isEqualTo(31L);
        assertThat(task.getClosedReason()).isEqualTo("USER_OFFBOARDING_REASSIGNED");
        assertThat(instance.getCurrentApproverId()).isEqualTo(31L);
        verify(approvalTaskMapper).updateById(task);
        verify(approvalInstanceMapper).updateById(instance);
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue())
            .isInstanceOf(ApprovalTasksCreatedEvent.class)
            .extracting("purchaseRequestId")
            .isEqualTo(20L);
    }

    private ApprovalTask pendingTask(Long instanceId, int levelNo, Long userId, String passRule) {
        ApprovalTask task = new ApprovalTask();
        task.setId(userId);
        task.setInstanceId(instanceId);
        task.setLevelNo(levelNo);
        task.setCandidateUserId(userId);
        task.setPassRule(passRule);
        task.setStatus(ApprovalTaskService.STATUS_PENDING);
        return task;
    }
}
