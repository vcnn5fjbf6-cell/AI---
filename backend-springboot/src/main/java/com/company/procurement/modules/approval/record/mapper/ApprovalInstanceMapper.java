package com.company.procurement.modules.approval.record.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.procurement.modules.approval.record.entity.ApprovalInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ApprovalInstanceMapper extends BaseMapper<ApprovalInstance> {

    @Update("UPDATE approval_instance SET ended_at = NULL WHERE id = #{id} AND is_deleted = 0")
    int clearEndedAt(@Param("id") Long id);
}
