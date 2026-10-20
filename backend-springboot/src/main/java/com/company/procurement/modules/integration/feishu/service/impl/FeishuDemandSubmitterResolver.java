package com.company.procurement.modules.integration.feishu.service.impl;

import com.company.procurement.modules.procurement.request.entity.PurchaseRequest;
import com.company.procurement.modules.procurement.request.entity.PurchaseRequestItem;
import com.company.procurement.modules.procurement.request.mapper.PurchaseRequestMapper;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
class FeishuDemandSubmitterResolver {

    private final PurchaseRequestMapper purchaseRequestMapper;
    private final SysUserMapper userMapper;

    SubmitterContext resolve(PurchaseRequest request, List<PurchaseRequestItem> items) {
        if (request == null) {
            return SubmitterContext.empty();
        }
        List<PurchaseRequestItem> safeItems = items == null ? List.of() : items;
        LinkedHashSet<Long> sourceRequestIds = safeItems.stream()
            .map(item -> effectiveSourceRequestId(request, item))
            .filter(Objects::nonNull)
            .filter(id -> !Objects.equals(id, request.getId()))
            .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        Map<Long, PurchaseRequest> requestsById = new LinkedHashMap<>();
        if (request.getId() != null) {
            requestsById.put(request.getId(), request);
        }
        if (!sourceRequestIds.isEmpty()) {
            purchaseRequestMapper.selectBatchIds(sourceRequestIds).forEach(source -> {
                if (source != null && source.getId() != null) {
                    requestsById.put(source.getId(), source);
                }
            });
        }

        LinkedHashMap<Long, Long> applicantByItemId = new LinkedHashMap<>();
        LinkedHashSet<Long> applicantIds = new LinkedHashSet<>();
        for (PurchaseRequestItem item : safeItems) {
            Long applicantId = applicantId(request, item, requestsById);
            if (item != null && item.getId() != null && applicantId != null) {
                applicantByItemId.put(item.getId(), applicantId);
            }
            if (applicantId != null) {
                applicantIds.add(applicantId);
            }
        }
        if (applicantIds.isEmpty() && request.getApplicantUserId() != null) {
            applicantIds.add(request.getApplicantUserId());
        }

        Map<Long, SysUser> usersById = usersById(applicantIds);
        LinkedHashMap<Long, String> itemSubmitters = new LinkedHashMap<>();
        applicantByItemId.forEach((itemId, applicantId) ->
            itemSubmitters.put(itemId, displayName(usersById.get(applicantId), applicantId)));

        List<String> submitterNames = applicantIds.stream()
            .map(applicantId -> displayName(usersById.get(applicantId), applicantId))
            .toList();
        return new SubmitterContext(summary(submitterNames), Map.copyOf(itemSubmitters), submitterNames.size());
    }

    private Long effectiveSourceRequestId(PurchaseRequest request, PurchaseRequestItem item) {
        if (item != null && item.getSourceRequestId() != null) {
            return item.getSourceRequestId();
        }
        return request.getId();
    }

    private Long applicantId(PurchaseRequest request,
                             PurchaseRequestItem item,
                             Map<Long, PurchaseRequest> requestsById) {
        Long sourceRequestId = effectiveSourceRequestId(request, item);
        PurchaseRequest sourceRequest = sourceRequestId == null ? request : requestsById.get(sourceRequestId);
        return sourceRequest == null ? null : sourceRequest.getApplicantUserId();
    }

    private Map<Long, SysUser> usersById(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<Long, SysUser> result = new LinkedHashMap<>();
        userMapper.selectBatchIds(userIds).forEach(user -> {
            if (user != null && user.getId() != null) {
                result.put(user.getId(), user);
            }
        });
        return result;
    }

    private String displayName(SysUser user, Long userId) {
        if (user != null && StringUtils.hasText(user.getRealName())) {
            return user.getRealName().trim();
        }
        if (user != null && StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        return userId == null ? "-" : "用户ID " + userId;
    }

    private String summary(List<String> names) {
        if (names == null || names.isEmpty()) {
            return "-";
        }
        if (names.size() <= 2) {
            return String.join("、", names);
        }
        return names.get(0) + "、" + names.get(1) + "等" + names.size() + "人";
    }

    record SubmitterContext(String summary, Map<Long, String> itemSubmitters, int submitterCount) {

        static SubmitterContext empty() {
            return new SubmitterContext("-", Map.of(), 0);
        }

        boolean hasMultipleSubmitters() {
            return submitterCount > 1;
        }

        String itemSubmitter(Long itemId) {
            return itemId == null ? null : itemSubmitters.get(itemId);
        }
    }
}
