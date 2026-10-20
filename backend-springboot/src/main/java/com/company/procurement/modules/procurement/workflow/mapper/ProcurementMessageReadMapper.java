package com.company.procurement.modules.procurement.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.procurement.modules.procurement.workflow.entity.ProcurementMessageRead;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProcurementMessageReadMapper extends BaseMapper<ProcurementMessageRead> {

    @Insert("""
        INSERT INTO procurement_message_read
        (user_id, message_key, action_type, created_at, updated_at, created_by, updated_by, is_deleted, version)
        VALUES (#{userId}, #{messageKey}, 'READ', NOW(), NOW(), #{userId}, #{userId}, 0, 0)
        ON DUPLICATE KEY UPDATE
          action_type = IF(action_type = 'CLEARED', action_type, VALUES(action_type)),
          updated_at = NOW(),
          updated_by = VALUES(updated_by)
        """)
    int upsertRead(@Param("userId") Long userId, @Param("messageKey") String messageKey);

    @Insert("""
        INSERT INTO procurement_message_read
        (user_id, message_key, action_type, created_at, updated_at, created_by, updated_by, is_deleted, version)
        VALUES (#{userId}, #{messageKey}, 'CLEARED', NOW(), NOW(), #{userId}, #{userId}, 0, 0)
        ON DUPLICATE KEY UPDATE
          action_type = VALUES(action_type),
          updated_at = NOW(),
          updated_by = VALUES(updated_by)
        """)
    int upsertCleared(@Param("userId") Long userId, @Param("messageKey") String messageKey);
}
