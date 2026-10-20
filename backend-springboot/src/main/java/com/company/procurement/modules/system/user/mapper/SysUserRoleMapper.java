package com.company.procurement.modules.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.procurement.common.security.PermissionApi;
import com.company.procurement.modules.system.user.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("""
        SELECT id, user_id, role_id, status, effective_from, effective_to,
               created_at, updated_at, created_by, updated_by, is_deleted, version
        FROM sys_user_role
        WHERE user_id = #{userId}
        """)
    List<SysUserRole> selectAllByUserIdIncludingDeleted(@Param("userId") Long userId);

    @Update("""
        UPDATE sys_user_role
        SET status = #{status},
            effective_from = NULL,
            effective_to = NULL,
            is_deleted = #{isDeleted},
            updated_at = NOW(),
            updated_by = #{updatedBy},
            version = version + 1
        WHERE user_id = #{userId}
          AND role_id = #{roleId}
        """)
    int updateBindingState(@Param("userId") Long userId,
                           @Param("roleId") Long roleId,
                           @Param("status") Integer status,
                           @Param("isDeleted") Integer isDeleted,
                           @Param("updatedBy") Long updatedBy);

    @Select("""
        SELECT DISTINCT r.role_code
        FROM sys_user_role ur
        JOIN sys_role r ON ur.role_id = r.id
        WHERE ur.user_id = #{userId}
          AND ur.status = 1
          AND ur.is_deleted = 0
          AND r.status = 1
          AND r.is_deleted = 0
          AND (ur.effective_from IS NULL OR ur.effective_from <= NOW())
          AND (ur.effective_to IS NULL OR ur.effective_to >= NOW())
        """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT p.perm_code
        FROM sys_user_role ur
        JOIN sys_role r ON ur.role_id = r.id
        JOIN sys_role_permission rp ON ur.role_id = rp.role_id
        JOIN sys_permission p ON rp.permission_id = p.id
        WHERE ur.user_id = #{userId}
          AND ur.status = 1
          AND ur.is_deleted = 0
          AND r.status = 1
          AND r.is_deleted = 0
          AND rp.status = 1
          AND rp.is_deleted = 0
          AND p.status = 1
          AND p.is_deleted = 0
          AND (ur.effective_from IS NULL OR ur.effective_from <= NOW())
          AND (ur.effective_to IS NULL OR ur.effective_to >= NOW())
        """)
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT
            p.perm_code AS permCode,
            p.path AS path,
            p.http_method AS httpMethod
        FROM sys_user_role ur
        JOIN sys_role r ON ur.role_id = r.id
        JOIN sys_role_permission rp ON ur.role_id = rp.role_id
        JOIN sys_permission p ON rp.permission_id = p.id
        WHERE ur.user_id = #{userId}
          AND ur.status = 1
          AND ur.is_deleted = 0
          AND r.status = 1
          AND r.is_deleted = 0
          AND rp.status = 1
          AND rp.is_deleted = 0
          AND p.status = 1
          AND p.is_deleted = 0
          AND p.path IS NOT NULL
          AND p.path <> ''
          AND (ur.effective_from IS NULL OR ur.effective_from <= NOW())
          AND (ur.effective_to IS NULL OR ur.effective_to >= NOW())
        """)
    List<PermissionApi> selectPermissionApisByUserId(@Param("userId") Long userId);

    @Select("""
        SELECT u.id
        FROM sys_user_role ur
        JOIN sys_role r ON ur.role_id = r.id
        JOIN sys_user u ON ur.user_id = u.id
        WHERE r.role_code = #{roleCode}
          AND ur.status = 1
          AND ur.is_deleted = 0
          AND r.status = 1
          AND r.is_deleted = 0
          AND u.status = 1
          AND u.is_deleted = 0
          AND (#{regionId} IS NULL OR u.region_id = #{regionId})
          AND (ur.effective_from IS NULL OR ur.effective_from <= NOW())
          AND (ur.effective_to IS NULL OR ur.effective_to >= NOW())
        ORDER BY u.id ASC
        LIMIT 1
        """)
    Long selectFirstActiveUserIdByRoleCodeAndRegion(@Param("roleCode") String roleCode, @Param("regionId") Long regionId);
}
