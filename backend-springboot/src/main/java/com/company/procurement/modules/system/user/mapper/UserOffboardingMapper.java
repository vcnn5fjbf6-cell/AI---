package com.company.procurement.modules.system.user.mapper;

import com.company.procurement.modules.system.user.vo.UserOffboardingImpactItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserOffboardingMapper {

    @Select("""
        SELECT CONCAT('CONFIG:', binding.id) AS impactKey,
               'APPROVAL_CONFIG' AS impactType,
               binding.id AS referenceId,
               level.id AS levelId,
               scope.scope_code AS scopeCode,
               COALESCE(scope.scope_name, policy.policy_name, policy.policy_code) AS scopeName,
               level.level_code AS levelCode,
               level.level_name AS levelName,
               level.role_code AS roleCode,
               CASE WHEN policy.enabled = 1 AND level.enabled = 1 THEN 1 ELSE 0 END AS nodeEnabled,
               NULL AS businessNo,
               NULL AS businessTitle
        FROM procurement_approval_level_user binding
        JOIN procurement_approval_level level
          ON level.id = binding.level_id
         AND level.is_deleted = 0
        JOIN procurement_approval_policy policy
          ON policy.id = level.policy_id
         AND policy.is_deleted = 0
        LEFT JOIN org_scope scope
          ON scope.id = policy.scope_id
         AND scope.is_deleted = 0
        WHERE binding.user_id = #{userId}
          AND binding.status = 1
          AND binding.is_deleted = 0
        ORDER BY COALESCE(scope.scope_name, policy.policy_name, policy.policy_code), level.level_no, binding.id
        """)
    List<UserOffboardingImpactItemVO> selectApprovalConfigImpacts(@Param("userId") Long userId);

    @Select("""
        SELECT CONCAT('TASK:', task.id) AS impactKey,
               'PENDING_TASK' AS impactType,
               task.id AS referenceId,
               NULL AS levelId,
               NULL AS scopeCode,
               request.dept_name AS scopeName,
               task.level_code AS levelCode,
               task.level_name AS levelName,
               task.role_code AS roleCode,
               1 AS nodeEnabled,
               request.req_no AS businessNo,
               request.req_title AS businessTitle
        FROM approval_task task
        JOIN purchase_request request
          ON request.id = task.purchase_request_id
         AND request.is_deleted = 0
        WHERE task.candidate_user_id = #{userId}
          AND task.status = 'PENDING'
          AND task.is_deleted = 0
        ORDER BY task.created_at, task.id
        """)
    List<UserOffboardingImpactItemVO> selectPendingTaskImpacts(@Param("userId") Long userId);
}
