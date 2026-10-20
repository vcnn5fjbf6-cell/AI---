package com.company.procurement.modules.ehs.service;

import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.modules.approval.record.service.ApprovalTaskService;
import com.company.procurement.modules.ehs.dto.EhsPermitApprovalDTO;
import com.company.procurement.modules.ehs.dto.EhsPermitCloseDTO;
import com.company.procurement.modules.ehs.entity.EhsPermit;
import com.company.procurement.modules.ehs.mapper.EhsPermitMapper;
import com.company.procurement.modules.procurement.route.entity.ProcurementApprovalRoute;
import com.company.procurement.modules.procurement.route.service.ProcurementApprovalRouteResolver;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.system.user.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EhsPermitServiceTest {

    @Mock private EhsPermitMapper permitMapper;
    @Mock private ApprovalInstanceMapper approvalInstanceMapper;
    @Mock private ApprovalRecordMapper approvalRecordMapper;
    @Mock private ApprovalTaskService approvalTaskService;
    @Mock private SysUserMapper userMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private EhsNotificationService notificationService;
    @Mock private ProcurementApprovalRouteResolver approvalRouteResolver;
    @Mock private EhsDepartmentOptionService departmentOptionService;
    @Mock private EhsPublicAccessService publicAccessService;

    private EhsPermitService service;

    @BeforeEach
    void setUp() {
        service = new EhsPermitService(
            permitMapper,
            approvalInstanceMapper,
            approvalRecordMapper,
            approvalTaskService,
            userMapper,
            userRoleMapper,
            notificationService,
            approvalRouteResolver,
            departmentOptionService,
            publicAccessService,
            new ObjectMapper()
        );
        ProcurementApprovalRoute route = new ProcurementApprovalRoute();
        route.setId(9L);
        route.setEnabled(1);
        route.setDeptCode("D001");
        route.setDeptName("测试部门");
        org.mockito.Mockito.lenient().when(departmentOptionService.requireEnabledRoute(9L)).thenReturn(route);
        org.mockito.Mockito.lenient().when(approvalRouteResolver.findRoute(any())).thenReturn(route);
        org.mockito.Mockito.lenient().when(approvalRouteResolver.firstDemandNodeNo(any())).thenReturn(3);
        org.mockito.Mockito.lenient().when(approvalRouteResolver.totalDemandApprovalNodes(any(), any())).thenReturn(4);
        org.mockito.Mockito.lenient().when(approvalRouteResolver.resolveAssignmentForNode(any(), org.mockito.ArgumentMatchers.eq(3)))
            .thenReturn(assignment(3, 2L, "楼长审核"));
        org.mockito.Mockito.lenient().when(approvalRouteResolver.resolveAssignmentForNode(any(), org.mockito.ArgumentMatchers.eq(4)))
            .thenReturn(assignment(4, 3L, "部门经理审核"));
        org.mockito.Mockito.lenient().when(approvalRouteResolver.nextActiveNodeNo(any(), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq(4))).thenReturn(4);
        org.mockito.Mockito.lenient().when(approvalRouteResolver.nextActiveNodeNo(any(), org.mockito.ArgumentMatchers.eq(4), org.mockito.ArgumentMatchers.eq(4))).thenReturn(0);
        org.mockito.Mockito.lenient().when(approvalTaskService.businessApprovalDueAt(any())).thenAnswer(invocation -> ((LocalDateTime) invocation.getArgument(0)).plusDays(4));
        org.mockito.Mockito.lenient().when(approvalTaskService.completeBusinessTask(any(), any(), any(), any(), any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(publicAccessService.issueApproverToken(any(), any()))
            .thenReturn(new EhsPublicAccessService.IssuedToken(1L, "public-token", LocalDateTime.now().plusDays(7)));
        org.mockito.Mockito.lenient().when(publicAccessService.reviewUrl(any())).thenReturn("");
        AuthContextHolder.set(AuthUser.builder()
            .userId(1L)
            .username("applicant")
            .realName("申请人")
            .roleCodes(List.of("ENGINEER"))
            .build());
    }

    @AfterEach
    void tearDown() {
        AuthContextHolder.clear();
    }

    @Test
    void submitParentCreatesRequiredChildAndSharedApprovalTasks() {
        EhsPermit parent = parentPermit("DRAFT");
        parent.setRequiredPermitTypesJson("[\"HOT_WORK\"]");
        List<EhsPermit> insertedChildren = new ArrayList<>();
        AtomicLong instanceIds = new AtomicLong(100L);

        when(permitMapper.selectById(1L)).thenReturn(parent);
        when(permitMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(insertedChildren));
        org.mockito.Mockito.doAnswer(invocation -> {
            EhsPermit permit = invocation.getArgument(0);
            permit.setId(2L);
            insertedChildren.add(permit);
            return 1;
        }).when(permitMapper).insert(any(EhsPermit.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            ApprovalInstance instance = invocation.getArgument(0);
            instance.setId(instanceIds.getAndIncrement());
            return 1;
        }).when(approvalInstanceMapper).insert(any(ApprovalInstance.class));

        service.submit(1L);

        assertThat(insertedChildren).singleElement().satisfies(child -> {
            assertThat(child.getPermitType()).isEqualTo("HOT_WORK");
            assertThat(child.getParentId()).isEqualTo(1L);
            assertThat(child.getStatus()).isEqualTo("PENDING_APPROVAL");
            assertThat(child.getApprovalInstanceId()).isNotNull();
        });
        assertThat(parent.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(parent.getApprovalStatus()).isEqualTo("APPROVING");
        verify(approvalTaskService, org.mockito.Mockito.times(2)).createBusinessTasks(
            any(), org.mockito.ArgumentMatchers.argThat(item -> item.levelNo() == 3), any(), any(), any()
        );
        verify(approvalRecordMapper, atLeastOnce()).insert(any(com.company.procurement.modules.approval.record.entity.ApprovalRecord.class));
    }

    @Test
    void parentCannotCloseBeforeEveryChildPermitIsClosed() {
        EhsPermit parent = parentPermit("IN_PROGRESS");
        EhsPermit child = parentPermit("IN_PROGRESS");
        child.setId(2L);
        child.setParentId(1L);
        child.setPermitType("HOT_WORK");
        when(permitMapper.selectById(1L)).thenReturn(parent);
        when(permitMapper.selectList(any())).thenReturn(List.of(child));
        EhsPermitCloseDTO close = new EhsPermitCloseDTO();
        close.setOutcome("NORMAL");
        close.setSignature("申请人");
        close.setActualEndAt(LocalDateTime.now());

        assertThatThrownBy(() -> service.close(1L, close))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("专项票关闭");
    }

    @Test
    void approvalAdvancesToTheNextConfiguredApprover() {
        AuthContextHolder.set(AuthUser.builder()
            .userId(2L)
            .username("approver1")
            .realName("一级审批人")
            .roleCodes(List.of("EHS_APPROVER"))
            .build());
        EhsPermit permit = parentPermit("PENDING_APPROVAL");
        permit.setApprovalStatus("APPROVING");
        permit.setApprovalInstanceId(100L);
        permit.setCurrentLevelNo(3);
        permit.setCurrentApproverId(2L);
        permit.setApprovalSnapshotJson("[{\"levelNo\":3,\"levelCode\":\"V3\",\"levelName\":\"楼长审核\",\"roleCode\":\"FLOOR_LEADER\",\"passRule\":\"ANY_ONE\",\"candidateUserIds\":[2],\"candidateNames\":\"一级审批人\"},{\"levelNo\":4,\"levelCode\":\"V4\",\"levelName\":\"部门经理审核\",\"roleCode\":\"MANAGER\",\"passRule\":\"ANY_ONE\",\"candidateUserIds\":[3],\"candidateNames\":\"二级审批人\"}]");
        permit.setSubmitRound(1);
        ApprovalInstance instance = new ApprovalInstance();
        instance.setId(100L);
        instance.setCurrentNodeNo(3);
        instance.setCurrentLevelNo(3);
        instance.setCurrentApproverId(2L);
        instance.setStatus("APPROVING");
        when(permitMapper.selectById(1L)).thenReturn(permit);
        when(approvalInstanceMapper.selectById(100L)).thenReturn(instance);
        EhsPermitApprovalDTO approval = new EhsPermitApprovalDTO();
        approval.setAction("APPROVE");
        approval.setComment("同意");

        service.approve(1L, approval);

        assertThat(permit.getApprovalStatus()).isEqualTo("APPROVING");
        assertThat(permit.getCurrentLevelNo()).isEqualTo(4);
        assertThat(permit.getCurrentApproverId()).isEqualTo(3L);
        assertThat(instance.getCurrentLevelNo()).isEqualTo(4);
        verify(approvalTaskService).completeBusinessTask(
            org.mockito.ArgumentMatchers.same(instance),
            org.mockito.ArgumentMatchers.same(AuthContextHolder.requireUser()),
            org.mockito.ArgumentMatchers.eq("APPROVE"),
            org.mockito.ArgumentMatchers.eq("同意"),
            org.mockito.ArgumentMatchers.any()
        );
        verify(approvalTaskService, times(1)).createBusinessTasks(
            any(), org.mockito.ArgumentMatchers.argThat(item -> item.levelNo() == 4 && item.candidateUserIds().equals(List.of(3L))), any(), any(), any()
        );
    }

    @Test
    void resubmitRecordsReturnedAsTheAuditSourceStatus() {
        EhsPermit permit = parentPermit("RETURNED");
        permit.setRequiredPermitTypesJson("[]");
        when(permitMapper.selectById(1L)).thenReturn(permit);
        when(permitMapper.selectList(any())).thenReturn(List.of());
        org.mockito.Mockito.doAnswer(invocation -> {
            ApprovalInstance instance = invocation.getArgument(0);
            instance.setId(101L);
            return 1;
        }).when(approvalInstanceMapper).insert(any(ApprovalInstance.class));

        service.submit(1L);

        verify(approvalRecordMapper).insert(org.mockito.ArgumentMatchers.<com.company.procurement.modules.approval.record.entity.ApprovalRecord>argThat(record ->
            "SUBMIT".equals(record.getAction())
                && "RETURNED".equals(record.getFromStatus())
                && "APPROVING".equals(record.getToStatus())
        ));
    }

    private EhsPermit parentPermit(String status) {
        EhsPermit permit = new EhsPermit();
        permit.setId(1L);
        permit.setPermitNo("PTW202607200001");
        permit.setPermitType("WORK_PERMIT");
        permit.setTitle("测试作业");
        permit.setApplicantUserId(1L);
        permit.setApplicantName("申请人");
        permit.setLocation("1号楼");
        permit.setWorkContent("设备检修");
        permit.setPlannedStartAt(LocalDateTime.now().plusHours(1));
        permit.setPlannedEndAt(LocalDateTime.now().plusHours(8));
        permit.setStatus(status);
        permit.setApprovalStatus("DRAFT");
        permit.setSubmitRound(0);
        permit.setApprovalRouteId(9L);
        permit.setRiskItemsJson("[]");
        permit.setSafetyNoticeJson("{}");
        permit.setOperationStepsJson("[]");
        permit.setSignaturesJson("[]");
        permit.setAttachmentsJson("[]");
        return permit;
    }

    private ProcurementApprovalRouteResolver.ApprovalAssignment assignment(int levelNo, Long userId, String levelName) {
        return new ProcurementApprovalRouteResolver.ApprovalAssignment(
            userId,
            List.of(userId),
            levelName,
            true,
            null,
            null,
            levelNo,
            "V" + levelNo,
            levelName,
            ProcurementApprovalRouteResolver.PASS_RULE_ANY_ONE,
            true
        );
    }
}
