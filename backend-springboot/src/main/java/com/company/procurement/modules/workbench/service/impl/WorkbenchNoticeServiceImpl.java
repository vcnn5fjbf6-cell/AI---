package com.company.procurement.modules.workbench.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.workbench.dto.WorkbenchNoticeSaveDTO;
import com.company.procurement.modules.workbench.entity.WorkbenchNotice;
import com.company.procurement.modules.workbench.mapper.WorkbenchNoticeMapper;
import com.company.procurement.modules.workbench.service.WorkbenchNoticeService;
import com.company.procurement.modules.workbench.vo.WorkbenchNoticeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class WorkbenchNoticeServiceImpl extends ServiceImpl<WorkbenchNoticeMapper, WorkbenchNotice> implements WorkbenchNoticeService {

    public static final String MANAGE_PERMISSION = "workbench_notice:manage";
    private static final String WILDCARD_PERMISSION_CODE = "*:*:*";

    @Override
    public List<WorkbenchNoticeVO> listVisible() {
        AuthUser authUser = AuthContextHolder.requireUser();
        if (DataScopeHelper.isSupplierUser(authUser)) {
            return List.of();
        }
        return baseMapper.selectList(new LambdaQueryWrapper<WorkbenchNotice>()
                .eq(WorkbenchNotice::getStatus, 1)
                .orderByDesc(WorkbenchNotice::getPinned)
                .orderByAsc(WorkbenchNotice::getSortNo)
                .orderByDesc(WorkbenchNotice::getUpdatedAt)
                .last("LIMIT 20"))
            .stream()
            .map(this::toVO)
            .toList();
    }

    @Override
    public List<WorkbenchNoticeVO> listManage() {
        ensureManagePermission();
        return baseMapper.selectList(new LambdaQueryWrapper<WorkbenchNotice>()
                .orderByDesc(WorkbenchNotice::getPinned)
                .orderByAsc(WorkbenchNotice::getSortNo)
                .orderByDesc(WorkbenchNotice::getUpdatedAt)
                .last("LIMIT 100"))
            .stream()
            .map(this::toVO)
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkbenchNoticeVO create(WorkbenchNoticeSaveDTO dto) {
        ensureManagePermission();
        WorkbenchNotice notice = new WorkbenchNotice();
        applyDTO(notice, dto);
        baseMapper.insert(notice);
        return toVO(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkbenchNoticeVO update(Long id, WorkbenchNoticeSaveDTO dto) {
        ensureManagePermission();
        WorkbenchNotice notice = baseMapper.selectById(id);
        if (notice == null) {
            throw new BizException("WORKBENCH_NOTICE_NOT_FOUND", "公告不存在");
        }
        applyDTO(notice, dto);
        baseMapper.updateById(notice);
        return toVO(baseMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ensureManagePermission();
        if (baseMapper.selectById(id) == null) {
            throw new BizException("WORKBENCH_NOTICE_NOT_FOUND", "公告不存在");
        }
        baseMapper.deleteById(id);
    }

    private void applyDTO(WorkbenchNotice notice, WorkbenchNoticeSaveDTO dto) {
        notice.setTitle(dto.getTitle().trim());
        notice.setContent(dto.getContent().trim());
        notice.setPinned(Boolean.TRUE.equals(dto.getPinned()) ? 1 : 0);
        notice.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        notice.setSortNo(dto.getSortNo() == null ? 100 : dto.getSortNo());
    }

    private void ensureManagePermission() {
        AuthUser authUser = AuthContextHolder.requireUser();
        if (DataScopeHelper.isSystemAdmin(authUser)) {
            return;
        }
        if (authUser.getPermissionCodes() == null) {
            throw new BizException("FORBIDDEN", "当前账号没有工作台公告维护权限");
        }
        boolean allowed = authUser.getPermissionCodes().stream()
            .filter(StringUtils::hasText)
            .map(code -> code.trim().toLowerCase(Locale.ROOT))
            .anyMatch(code -> MANAGE_PERMISSION.equals(code) || WILDCARD_PERMISSION_CODE.equals(code));
        if (!allowed) {
            throw new BizException("FORBIDDEN", "当前账号没有工作台公告维护权限");
        }
    }

    private WorkbenchNoticeVO toVO(WorkbenchNotice notice) {
        WorkbenchNoticeVO vo = new WorkbenchNoticeVO();
        vo.setId(notice.getId());
        vo.setTitle(notice.getTitle());
        vo.setContent(notice.getContent());
        vo.setPinned(Integer.valueOf(1).equals(notice.getPinned()));
        vo.setStatus(notice.getStatus());
        vo.setSortNo(notice.getSortNo());
        vo.setUpdatedAt(notice.getUpdatedAt());
        return vo;
    }
}
