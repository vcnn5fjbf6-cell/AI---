package com.company.procurement.modules.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.procurement.modules.supplier.entity.SupplierQuote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SupplierQuoteMapper extends BaseMapper<SupplierQuote> {

    @Select("""
        SELECT COUNT(DISTINCT purchase_request_id)
        FROM supplier_quote
        WHERE status = 'SUBMITTED'
          AND is_deleted = 0
        """)
    long countDistinctSubmittedRequestCount();

    @Select("""
        SELECT COUNT(*)
        FROM supplier_quote
        WHERE supplier_user_id = #{supplierUserId}
          AND status = 'SUBMITTED'
          AND is_deleted = 0
        """)
    long countSubmittedBySupplierUserId(@Param("supplierUserId") Long supplierUserId);
}
