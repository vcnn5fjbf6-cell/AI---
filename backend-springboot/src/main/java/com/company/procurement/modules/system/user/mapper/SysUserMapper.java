package com.company.procurement.modules.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.procurement.modules.system.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
        SELECT *
        FROM sys_user
        WHERE username = #{username}
        LIMIT 1
        """)
    SysUser selectAnyByUsername(@Param("username") String username);

    @Select("""
        SELECT *
        FROM sys_user
        WHERE employee_no = #{employeeNo}
        LIMIT 1
        """)
    SysUser selectAnyByEmployeeNo(@Param("employeeNo") String employeeNo);

    @Select("""
        SELECT *
        FROM sys_user
        WHERE mobile = #{mobile}
        LIMIT 1
        """)
    SysUser selectAnyByMobile(@Param("mobile") String mobile);

    @Select("""
        SELECT *
        FROM sys_user
        WHERE email = #{email}
        LIMIT 1
        """)
    SysUser selectAnyByEmail(@Param("email") String email);

    @Select("""
        SELECT *
        FROM sys_user
        WHERE username = #{username}
          AND is_deleted = 1
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    SysUser selectDeletedByUsername(@Param("username") String username);

    @Select("""
        SELECT *
        FROM sys_user
        WHERE employee_no = #{employeeNo}
          AND is_deleted = 1
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    SysUser selectDeletedByEmployeeNo(@Param("employeeNo") String employeeNo);

    @Select("""
        SELECT *
        FROM sys_user
        WHERE mobile = #{mobile}
          AND is_deleted = 1
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    SysUser selectDeletedByMobile(@Param("mobile") String mobile);

    @Select("""
        SELECT *
        FROM sys_user
        WHERE email = #{email}
          AND is_deleted = 1
        ORDER BY updated_at DESC, id DESC
        LIMIT 1
        """)
    SysUser selectDeletedByEmail(@Param("email") String email);

    @Select("""
        <script>
        SELECT COUNT(1)
        FROM sys_user
        WHERE username = #{username}
        <if test="excludeId != null">
          AND id &lt;&gt; #{excludeId}
        </if>
        </script>
        """)
    long countAnyByUsernameExcept(@Param("username") String username, @Param("excludeId") Long excludeId);

    @Select("""
        <script>
        SELECT COUNT(1)
        FROM sys_user
        WHERE employee_no = #{employeeNo}
        <if test="excludeId != null">
          AND id &lt;&gt; #{excludeId}
        </if>
        </script>
        """)
    long countAnyByEmployeeNoExcept(@Param("employeeNo") String employeeNo, @Param("excludeId") Long excludeId);

    @Select("""
        <script>
        SELECT COUNT(1)
        FROM sys_user
        WHERE mobile = #{mobile}
        <if test="excludeId != null">
          AND id &lt;&gt; #{excludeId}
        </if>
        </script>
        """)
    long countAnyByMobileExcept(@Param("mobile") String mobile, @Param("excludeId") Long excludeId);

    @Select("""
        <script>
        SELECT COUNT(1)
        FROM sys_user
        WHERE email = #{email}
        <if test="excludeId != null">
          AND id &lt;&gt; #{excludeId}
        </if>
        </script>
        """)
    long countAnyByEmailExcept(@Param("email") String email, @Param("excludeId") Long excludeId);

    @Update("""
        UPDATE sys_user
        SET email = NULL,
            updated_at = NOW(),
            updated_by = COALESCE(#{updatedBy}, updated_by, 0),
            version = version + 1
        WHERE id = #{id}
        """)
    int clearEmailById(@Param("id") Long id, @Param("updatedBy") Long updatedBy);

    @Update("""
        UPDATE sys_user
        SET username = #{username},
            password_hash = #{passwordHash},
            real_name = #{realName},
            employee_no = #{employeeNo},
            user_type = #{userType},
            supplier_id = #{supplierId},
            region_id = #{regionId},
            dept_code = #{deptCode},
            dept_name = #{deptName},
            mobile = #{mobile},
            email = #{email},
            feishu_user_id = #{feishuUserId},
            feishu_open_id = #{feishuOpenId},
            feishu_union_id = #{feishuUnionId},
            status = #{status},
            data_scope_type = #{dataScopeType},
            must_change_password = #{mustChangePassword},
            password_changed_at = #{passwordChangedAt},
            remark = #{remark},
            updated_at = NOW(),
            updated_by = COALESCE(#{updatedBy}, updated_by, 0),
            is_deleted = 0,
            version = version + 1
        WHERE id = #{id}
        """)
    int restoreDeletedUser(SysUser user);
}
