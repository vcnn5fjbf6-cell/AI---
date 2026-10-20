package com.company.procurement.modules.system.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.system.user.dto.FeishuUserSyncDTO;
import com.company.procurement.modules.system.user.dto.UserCreateDTO;
import com.company.procurement.modules.system.user.dto.UserPageQueryDTO;
import com.company.procurement.modules.system.user.dto.UserUpdateDTO;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.vo.FeishuUserSyncResultVO;
import com.company.procurement.modules.system.user.vo.FeishuDepartmentSearchVO;
import com.company.procurement.modules.system.user.vo.UserImportResultVO;
import com.company.procurement.modules.system.user.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SysUserService extends IService<SysUser> {

    PageResult<UserVO> page(UserPageQueryDTO queryDTO);

    List<UserVO> list(UserPageQueryDTO queryDTO);

    UserVO detail(Long id);

    Long create(UserCreateDTO dto);

    void update(Long id, UserUpdateDTO dto);

    void remove(Long id);

    FeishuUserSyncResultVO syncFeishuUsers(FeishuUserSyncDTO dto);

    List<FeishuDepartmentSearchVO> searchFeishuDepartments(String keyword);

    UserImportResultVO importUsers(MultipartFile file);
}
