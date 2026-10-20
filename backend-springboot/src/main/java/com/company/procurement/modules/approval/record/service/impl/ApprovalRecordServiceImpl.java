package com.company.procurement.modules.approval.record.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.approval.record.dto.ApprovalRecordCreateDTO;
import com.company.procurement.modules.approval.record.dto.ApprovalRecordPageQueryDTO;
import com.company.procurement.modules.approval.record.dto.ApprovalRecordUpdateDTO;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import com.company.procurement.modules.approval.record.entity.ApprovalRecord;
import com.company.procurement.modules.approval.record.mapper.ApprovalInstanceMapper;
import com.company.procurement.modules.approval.record.mapper.ApprovalRecordMapper;
import com.company.procurement.modules.approval.record.service.ApprovalRecordService;
import com.company.procurement.modules.approval.record.vo.ApprovalRecordVO;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ApprovalRecordServiceImpl extends ServiceImpl<ApprovalRecordMapper, ApprovalRecord> implements ApprovalRecordService {

    private final ApprovalInstanceMapper instanceMapper;
    private final PurchaseRequestMapper purchaseRequestMapper;

    @Override
    public PageResult<ApprovalRecordVO> page(ApprovalRecordPageQueryDTO queryDTO) {
        LambdaQueryWrapper<ApprovalRecord> wrapper = buildQuery(queryDTO);
        Page<ApprovalRecord> page = baseMapper.selectPage(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        List<ApprovalRecordVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public List<ApprovalRecordVO> list(ApprovalRecordPageQueryDTO queryDTO) {
        return baseMapper.selectList(buildQuery(queryDTO)).stream().map(this::toVO).toList();
    }

    @Override
    public ApprovalRecordVO detail(Long id) {
        ApprovalRecord record = getById(id);
        if (record == null) {
            throw new BizException("APPROVAL_RECORD_NOT_FOUND", "Approval record not found");
        }
        return toVO(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ApprovalRecordCreateDTO dto) {
        ApprovalInstance instance = instanceMapper.selectById(dto.getInstanceId());
        if (instance == null) {
            throw new BizException("APPROVAL_INSTANCE_NOT_FOUND", "Approval instance not found");
        }

        Long purchaseRequestId = resolvePurchaseRequestId(dto.getPurchaseRequestId(), instance);
        PurchaseRequest purchaseRequest = null;
        if (purchaseRequestId != null) {
            purchaseRequest = purchaseRequestMapper.selectById(purchaseRequestId);
            if (purchaseRequest == null) {
                throw new BizException("PURCHASE_REQUEST_NOT_FOUND", "Purchase request not found");
            }
        }

        ApprovalRecord record = new ApprovalRecord();
        applyCreate(dto, record);
        record.setPurchaseRequestId(purchaseRequestId);
        save(record);

        refreshInstance(instance, record);
        if (purchaseRequest != null) {
            refreshPurchaseRequest(purchaseRequest, instance, record);
        }
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ApprovalRecordUpdateDTO dto) {
        ApprovalRecord record = getById(id);
        if (record == null) {
            throw new BizException("APPROVAL_RECORD_NOT_FOUND", "Approval record not found");
        }
        applyUpdate(dto, record);
        updateById(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        removeById(id);
    }

    private LambdaQueryWrapper<ApprovalRecord> buildQuery(ApprovalRecordPageQueryDTO queryDTO) {
        LambdaQueryWrapper<ApprovalRecord> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getInstanceId() != null) {
            wrapper.eq(ApprovalRecord::getInstanceId, queryDTO.getInstanceId());
        }
        if (queryDTO.getPurchaseRequestId() != null) {
            wrapper.eq(ApprovalRecord::getPurchaseRequestId, queryDTO.getPurchaseRequestId());
        }
        if (queryDTO.getApproverUserId() != null) {
            wrapper.eq(ApprovalRecord::getApproverUserId, queryDTO.getApproverUserId());
        }
        if (StringUtils.hasText(queryDTO.getAction())) {
            wrapper.eq(ApprovalRecord::getAction, queryDTO.getAction());
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(ApprovalRecord::getStatus, queryDTO.getStatus());
        }
        if (queryDTO.getStartTime() != null) {
            wrapper.ge(ApprovalRecord::getOperatedAt, queryDTO.getStartTime());
        }
        if (queryDTO.getEndTime() != null) {
            wrapper.le(ApprovalRecord::getOperatedAt, queryDTO.getEndTime());
        }
        wrapper.orderByDesc(ApprovalRecord::getOperatedAt);
        return wrapper;
    }

    private ApprovalRecordVO toVO(ApprovalRecord record) {
        ApprovalRecordVO vo = new ApprovalRecordVO();
        vo.setId(record.getId());
        vo.setInstanceId(record.getInstanceId());
        vo.setPurchaseRequestId(record.getPurchaseRequestId());
        vo.setNodeNo(record.getNodeNo());
        vo.setNodeName(record.getNodeName());
        vo.setApproverUserId(record.getApproverUserId());
        vo.setApproverRoleCode(record.getApproverRoleCode());
        vo.setAction(record.getAction());
        vo.setFromStatus(record.getFromStatus());
        vo.setToStatus(record.getToStatus());
        vo.setApprovalComment(record.getApprovalComment());
        vo.setOperatedAt(record.getOperatedAt());
        vo.setSubmitRound(record.getSubmitRound());
        vo.setIsFinalNode(record.getIsFinalNode());
        vo.setCanResubmit(record.getCanResubmit());
        vo.setTransferToUserId(record.getTransferToUserId());
        vo.setStatus(record.getStatus());
        vo.setRemark(record.getRemark());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private void applyCreate(ApprovalRecordCreateDTO dto, ApprovalRecord record) {
        record.setInstanceId(dto.getInstanceId());
        record.setPurchaseRequestId(dto.getPurchaseRequestId());
        record.setNodeNo(dto.getNodeNo());
        record.setNodeName(dto.getNodeName());
        record.setApproverUserId(dto.getApproverUserId());
        record.setApproverRoleCode(dto.getApproverRoleCode());
        record.setAction(dto.getAction());
        record.setFromStatus(dto.getFromStatus());
        record.setToStatus(dto.getToStatus());
        record.setApprovalComment(dto.getApprovalComment());
        record.setOperatedAt(dto.getOperatedAt() == null ? LocalDateTime.now() : dto.getOperatedAt());
        record.setSubmitRound(dto.getSubmitRound());
        record.setIsFinalNode(dto.getIsFinalNode());
        record.setCanResubmit(dto.getCanResubmit());
        record.setTransferToUserId(dto.getTransferToUserId());
        record.setStatus(dto.getStatus());
        record.setRemark(dto.getRemark());
    }

    private void applyUpdate(ApprovalRecordUpdateDTO dto, ApprovalRecord record) {
        record.setNodeNo(dto.getNodeNo());
        record.setNodeName(dto.getNodeName());
        record.setApproverUserId(dto.getApproverUserId());
        record.setApproverRoleCode(dto.getApproverRoleCode());
        record.setAction(dto.getAction());
        record.setFromStatus(dto.getFromStatus());
        record.setToStatus(dto.getToStatus());
        record.setApprovalComment(dto.getApprovalComment());
        record.setOperatedAt(dto.getOperatedAt() == null ? record.getOperatedAt() : dto.getOperatedAt());
        record.setSubmitRound(dto.getSubmitRound());
        record.setIsFinalNode(dto.getIsFinalNode());
        record.setCanResubmit(dto.getCanResubmit());
        record.setTransferToUserId(dto.getTransferToUserId());
        record.setStatus(dto.getStatus());
        record.setRemark(dto.getRemark());
    }

    private void refreshInstance(ApprovalInstance instance, ApprovalRecord record) {
        instance.setCurrentNodeNo(record.getNodeNo());
        instance.setLastAction(record.getAction());
        instance.setLastActionBy(record.getApproverUserId());
        instance.setLastActionAt(record.getOperatedAt());

        String action = normalizeAction(record.getAction());
        if ("APPROVE".equals(action)) {
            if (isFinalApprove(record)) {
                instance.setStatus("APPROVED");
                instance.setEndedAt(record.getOperatedAt());
                instance.setCurrentApproverId(null);
            } else {
                instance.setStatus("APPROVING");
                instance.setCurrentNodeNo(nextNodeNo(record, instance));
                instance.setCurrentApproverId(record.getTransferToUserId());
                instance.setEndedAt(null);
            }
        } else if ("REJECT".equals(action)) {
            instance.setStatus("REJECTED");
            instance.setEndedAt(record.getOperatedAt());
            instance.setCurrentApproverId(null);
        } else if ("RETURN".equals(action)) {
            instance.setStatus("RETURNED");
            instance.setEndedAt(record.getOperatedAt());
            instance.setCurrentApproverId(null);
        } else {
            instance.setStatus("APPROVING");
            instance.setCurrentApproverId(record.getTransferToUserId());
            instance.setEndedAt(null);
        }
        instanceMapper.updateById(instance);
        if (instance.getEndedAt() == null) {
            instanceMapper.clearEndedAt(instance.getId());
        }
    }

    private void refreshPurchaseRequest(PurchaseRequest purchaseRequest, ApprovalInstance instance, ApprovalRecord record) {
        String action = normalizeAction(record.getAction());
        LocalDateTime actionTime = record.getOperatedAt() == null ? LocalDateTime.now() : record.getOperatedAt();

        if ("APPROVE".equals(action)) {
            if (isFinalApprove(record)) {
                purchaseRequest.setApprovalStatus("APPROVED");
                purchaseRequest.setStatus("APPROVED");
                purchaseRequest.setCurrentNodeNo(record.getNodeNo());
                purchaseRequest.setApprovedAt(actionTime);
                purchaseRequest.setReturnedAt(null);
                purchaseRequest.setRejectedAt(null);
            } else {
                purchaseRequest.setApprovalStatus("PENDING");
                purchaseRequest.setStatus("PENDING_APPROVAL");
                purchaseRequest.setCurrentNodeNo(instance.getCurrentNodeNo());
            }
        } else if ("REJECT".equals(action)) {
            purchaseRequest.setApprovalStatus("REJECTED");
            purchaseRequest.setStatus("REJECTED");
            purchaseRequest.setCurrentNodeNo(record.getNodeNo());
            purchaseRequest.setRejectedAt(actionTime);
        } else if ("RETURN".equals(action)) {
            purchaseRequest.setApprovalStatus("RETURNED");
            purchaseRequest.setStatus("RETURNED");
            purchaseRequest.setCurrentNodeNo(record.getNodeNo());
            purchaseRequest.setReturnedAt(actionTime);
        } else {
            purchaseRequest.setApprovalStatus("PENDING");
            purchaseRequest.setStatus("PENDING_APPROVAL");
            purchaseRequest.setCurrentNodeNo(instance.getCurrentNodeNo());
        }

        purchaseRequestMapper.updateById(purchaseRequest);
    }

    private Long resolvePurchaseRequestId(Long dtoPurchaseRequestId, ApprovalInstance instance) {
        if (dtoPurchaseRequestId != null) {
            return dtoPurchaseRequestId;
        }
        if (instance.getPurchaseRequestId() != null) {
            return instance.getPurchaseRequestId();
        }
        if ("PURCHASE_REQUEST".equalsIgnoreCase(instance.getBizType())) {
            return instance.getBizId();
        }
        return null;
    }

    private boolean isFinalApprove(ApprovalRecord record) {
        return record.getIsFinalNode() != null && record.getIsFinalNode() == 1;
    }

    private int nextNodeNo(ApprovalRecord record, ApprovalInstance instance) {
        int currentNodeNo = record.getNodeNo() == null ? 0 : record.getNodeNo();
        int nextNodeNo = currentNodeNo + 1;
        Integer totalNodeNo = instance.getTotalNodeNo();
        if (totalNodeNo != null && totalNodeNo > 0) {
            return Math.min(nextNodeNo, totalNodeNo);
        }
        return nextNodeNo;
    }

    private String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            return "";
        }
        return action.trim().toUpperCase(Locale.ROOT);
    }
}
