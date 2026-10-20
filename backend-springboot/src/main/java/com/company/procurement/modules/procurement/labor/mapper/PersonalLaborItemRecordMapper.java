package com.company.procurement.modules.procurement.labor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.procurement.modules.procurement.labor.entity.PersonalLaborItemRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PersonalLaborItemRecordMapper extends BaseMapper<PersonalLaborItemRecord> {

    @Select({
        "SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END",
        "FROM personal_labor_item_record labor",
        "JOIN purchase_request source_request ON source_request.id = labor.purchase_request_id AND source_request.is_deleted = 0",
        "WHERE labor.id = #{recordId}",
        "  AND labor.is_deleted = 0",
        "  AND source_request.approval_status = 'APPROVED'"
    })
    boolean hasApprovedProcurement(@Param("recordId") Long recordId);

    @Select({
        "SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END",
        "FROM personal_labor_item_record labor",
        "JOIN purchase_request_item source_item",
        "  ON source_item.purchase_request_id = labor.purchase_request_id",
        "  AND source_item.is_deleted = 0",
        "  AND source_item.status = 'ACTIVE'",
        "  AND (",
        "    (labor.purchase_request_item_id IS NOT NULL AND source_item.id = labor.purchase_request_item_id)",
        "    OR (labor.purchase_request_item_id IS NULL AND source_item.item_code = CONCAT('LABOR-', labor.item_code))",
        "  )",
        "JOIN purchase_request_item fulfillment_item",
        "  ON (fulfillment_item.id = source_item.id OR fulfillment_item.source_request_item_id = source_item.id)",
        "  AND fulfillment_item.is_deleted = 0",
        "  AND fulfillment_item.status = 'ACTIVE'",
        "JOIN purchase_request fulfillment_request",
        "  ON fulfillment_request.id = fulfillment_item.purchase_request_id",
        "  AND fulfillment_request.is_deleted = 0",
        "  AND fulfillment_request.approval_status = 'APPROVED'",
        "  AND fulfillment_request.status = 'APPROVED'",
        "  AND fulfillment_request.leader_confirm_status = 'CONFIRMED'",
        "JOIN supplier_quote_item quote_item",
        "  ON quote_item.purchase_request_item_id = fulfillment_item.id",
        "  AND quote_item.is_deleted = 0",
        "  AND quote_item.awarded = 1",
        "  AND quote_item.status = 'ACTIVE'",
        "  AND quote_item.receipt_status = 'RECEIVED'",
        "  AND quote_item.received_at IS NOT NULL",
        "JOIN supplier_quote quote",
        "  ON quote.id = quote_item.quote_id",
        "  AND quote.is_deleted = 0",
        "  AND quote.status = 'SUBMITTED'",
        "  AND quote.supplier_confirm_status = 'CONFIRMED'",
        "WHERE labor.id = #{recordId}",
        "  AND labor.is_deleted = 0"
    })
    boolean hasCompletedDelivery(@Param("recordId") Long recordId);
}
