package com.company.procurement.modules.system.role.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.system.role.dto.RoleCreateDTO;
import com.company.procurement.modules.system.role.dto.RolePageQueryDTO;
import com.company.procurement.modules.system.role.dto.RolePermissionAssignDTO;
import com.company.procurement.modules.system.role.dto.RoleUpdateDTO;
import com.company.procurement.modules.system.role.entity.SysRole;
import com.company.procurement.modules.system.role.vo.PermissionVO;
import com.company.procurement.modules.system.role.vo.RoleVO;

import java.util.List;

public interface SysRoleService extends IService<SysRole> {

    PageResult<RoleVO> page(RolePageQueryDTO queryDTO);

    List<RoleVO> list(RolePageQueryDTO queryDTO);

    RoleVO detail(Long id);

    Long create(RoleCreateDTO dto);

    void update(Long id, RoleUpdateDTO dto);

    void remove(Long id);

    void assignPermissions(Long roleId, RolePermissionAssignDTO dto);

    List<PermissionVO> listAllPermissions();
}
