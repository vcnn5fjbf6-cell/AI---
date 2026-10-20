package com.company.procurement.modules.supplier.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.procurement.common.exception.BizException;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.common.security.AuthContextHolder;
import com.company.procurement.common.security.AuthUser;
import com.company.procurement.common.security.DataScopeHelper;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import com.company.procurement.modules.supplier.dto.SupplierBanDTO;
import com.company.procurement.modules.supplier.dto.SupplierCreateDTO;
import com.company.procurement.modules.supplier.dto.SupplierPageQueryDTO;
import com.company.procurement.modules.supplier.dto.SupplierUpdateDTO;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.entity.SupplierQualification;
import com.company.procurement.modules.supplier.mapper.SupplierMapper;
import com.company.procurement.modules.supplier.mapper.SupplierQualificationMapper;
import com.company.procurement.modules.supplier.service.SupplierQuoteService;
import com.company.procurement.modules.supplier.service.SupplierService;
import com.company.procurement.modules.supplier.vo.SupplierVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {

    private static final DateTimeFormatter SUPPLIER_CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SupplierQualificationMapper qualificationMapper;
    private final SysUserMapper userMapper;
    private final SupplierQuoteService supplierQuoteService;

    @Override
    public PageResult<SupplierVO> page(SupplierPageQueryDTO queryDTO) {
        LambdaQueryWrapper<Supplier> wrapper = buildQuery(queryDTO);
        Page<Supplier> page = baseMapper.selectPage(new Page<>(queryDTO.getPageNo(), queryDTO.getPageSize()), wrapper);
        List<SupplierVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public List<SupplierVO> list(SupplierPageQueryDTO queryDTO) {
        return baseMapper.selectList(buildQuery(queryDTO)).stream().map(this::toVO).toList();
    }

    @Override
    public SupplierVO detail(Long id) {
        Supplier supplier = getById(id);
        if (supplier == null) {
            throw new BizException("SUPPLIER_NOT_FOUND", "Supplier not found");
        }
        ensureCanReadSupplier(supplier);
        return toVO(supplier);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SupplierCreateDTO dto) {
        ensureCanWriteSupplier(dto.getRegionId());
        String supplierCode = resolveSupplierCode(dto.getSupplierCode(), null);
        checkUnique(supplierCode, dto.getTaxNo(), null);
        Supplier supplier = new Supplier();
        apply(dto, supplier, supplierCode);
        save(supplier);
        return supplier.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SupplierUpdateDTO dto) {
        Supplier supplier = getById(id);
        if (supplier == null) {
            throw new BizException("SUPPLIER_NOT_FOUND", "Supplier not found");
        }
        ensureCanReadSupplier(supplier);
        ensureCanWriteSupplier(dto.getRegionId());
        String supplierCode = resolveSupplierCode(dto.getSupplierCode(), id);
        checkUnique(supplierCode, dto.getTaxNo(), id);
        boolean wasBanned = supplier.getBlacklistFlag() != null && supplier.getBlacklistFlag() == 1;
        apply(dto, supplier, supplierCode);
        updateById(supplier);
        boolean isBanned = supplier.getBlacklistFlag() != null && supplier.getBlacklistFlag() == 1;
        if (!wasBanned && isBanned) {
            supplierQuoteService.reassignPendingAwardsForSupplier(supplier.getId(), "供应商已被管理员封禁，系统自动取消当前中标并递补");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseBiddingSuspension(Long id) {
        Supplier supplier = getById(id);
        if (supplier == null) {
            throw new BizException("SUPPLIER_NOT_FOUND", "Supplier not found");
        }
        ensureCanReadSupplier(supplier);
        ensureCanWriteSupplier(supplier.getRegionId());
        supplier.setBidSuspendedUntil(null);
        supplier.setBidSuspendedReason(null);
        updateById(supplier);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ban(Long id, SupplierBanDTO dto) {
        Supplier supplier = getById(id);
        if (supplier == null) {
            throw new BizException("SUPPLIER_NOT_FOUND", "Supplier not found");
        }
        ensureCanReadSupplier(supplier);
        ensureCanWriteSupplier(supplier.getRegionId());
        supplier.setBlacklistFlag(1);
        supplier.setBlacklistReason(StringUtils.hasText(dto == null ? null : dto.getBanReason())
            ? dto.getBanReason()
            : "管理员封禁供应商报价资格");
        updateById(supplier);
        supplierQuoteService.reassignPendingAwardsForSupplier(supplier.getId(), "供应商已被管理员封禁，系统自动取消当前中标并递补");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unban(Long id) {
        Supplier supplier = getById(id);
        if (supplier == null) {
            throw new BizException("SUPPLIER_NOT_FOUND", "Supplier not found");
        }
        ensureCanReadSupplier(supplier);
        ensureCanWriteSupplier(supplier.getRegionId());
        supplier.setBlacklistFlag(0);
        supplier.setBlacklistReason(null);
        updateById(supplier);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        Supplier supplier = getById(id);
        if (supplier == null) {
            return;
        }
        ensureCanReadSupplier(supplier);
        ensureCanWriteSupplier(supplier.getRegionId());
        if (linkedUserCount(id) > 0) {
            throw new BizException("SUPPLIER_HAS_LINKED_USERS", "Supplier has linked user accounts and cannot be deleted");
        }
        removeById(id);
    }

    private LambdaQueryWrapper<Supplier> buildQuery(SupplierPageQueryDTO queryDTO) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(queryDTO.getSupplierCode())) {
            wrapper.like(Supplier::getSupplierCode, queryDTO.getSupplierCode());
        }
        if (StringUtils.hasText(queryDTO.getSupplierName())) {
            wrapper.like(Supplier::getSupplierName, queryDTO.getSupplierName());
        }
        if (queryDTO.getRegionId() != null) {
            wrapper.eq(Supplier::getRegionId, queryDTO.getRegionId());
        }
        if (StringUtils.hasText(queryDTO.getStatus())) {
            wrapper.eq(Supplier::getStatus, queryDTO.getStatus());
        }
        if (StringUtils.hasText(queryDTO.getOnboardingStatus())) {
            wrapper.eq(Supplier::getOnboardingStatus, queryDTO.getOnboardingStatus());
        }
        if (queryDTO.getBlacklistFlag() != null) {
            wrapper.eq(Supplier::getBlacklistFlag, queryDTO.getBlacklistFlag());
        }
        applyDataScope(wrapper);
        wrapper.orderByDesc(Supplier::getCreatedAt);
        return wrapper;
    }

    private void applyDataScope(LambdaQueryWrapper<Supplier> wrapper) {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser == null || DataScopeHelper.isSuperAdmin(authUser)) {
            return;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            wrapper.eq(Supplier::getId, authUser.getSupplierId());
            return;
        }
        String scope = DataScopeHelper.resolveDataScope(authUser);
        if (DataScopeHelper.SCOPE_REGION.equals(scope)) {
            wrapper.eq(Supplier::getRegionId, authUser.getRegionId());
        } else if (DataScopeHelper.SCOPE_SELF.equals(scope)) {
            wrapper.eq(Supplier::getCreatedBy, authUser.getUserId());
        }
    }

    private void checkUnique(String supplierCode, String taxNo, Long excludeId) {
        LambdaQueryWrapper<Supplier> codeWrapper = new LambdaQueryWrapper<Supplier>().eq(Supplier::getSupplierCode, supplierCode);
        LambdaQueryWrapper<Supplier> taxWrapper = new LambdaQueryWrapper<Supplier>().eq(Supplier::getTaxNo, taxNo);
        if (excludeId != null) {
            codeWrapper.ne(Supplier::getId, excludeId);
            taxWrapper.ne(Supplier::getId, excludeId);
        }
        if (baseMapper.selectCount(codeWrapper) > 0) {
            throw new BizException("SUPPLIER_CODE_EXISTS", "Supplier code already exists");
        }
        if (baseMapper.selectCount(taxWrapper) > 0) {
            throw new BizException("SUPPLIER_TAX_NO_EXISTS", "Supplier tax number already exists");
        }
    }

    private void apply(SupplierCreateDTO dto, Supplier supplier, String supplierCode) {
        supplier.setSupplierCode(supplierCode);
        supplier.setSupplierName(dto.getSupplierName());
        supplier.setSupplierShortName(dto.getSupplierShortName());
        supplier.setTaxNo(dto.getTaxNo());
        supplier.setSupplierType(dto.getSupplierType());
        supplier.setRegionId(dto.getRegionId());
        supplier.setContactName(dto.getContactName());
        supplier.setContactPhone(dto.getContactPhone());
        supplier.setContactEmail(dto.getContactEmail());
        supplier.setAddress(dto.getAddress());
        supplier.setBankName(dto.getBankName());
        supplier.setBankAccount(dto.getBankAccount());
        supplier.setInvoiceTitle(dto.getInvoiceTitle());
        supplier.setDefaultPaymentTerm(dto.getDefaultPaymentTerm());
        supplier.setRatingLevel(dto.getRatingLevel());
        supplier.setOnboardingStatus(dto.getOnboardingStatus());
        supplier.setStatus(dto.getStatus());
        supplier.setBlacklistFlag(dto.getBlacklistFlag() == null ? 0 : dto.getBlacklistFlag());
        supplier.setDataIsolationFlag(dto.getDataIsolationFlag() == null ? 1 : dto.getDataIsolationFlag());
        supplier.setBlacklistReason(dto.getBlacklistReason());
        supplier.setEntryDate(dto.getEntryDate());
        supplier.setRemark(dto.getRemark());
    }

    private void apply(SupplierUpdateDTO dto, Supplier supplier, String supplierCode) {
        supplier.setSupplierCode(supplierCode);
        supplier.setSupplierName(dto.getSupplierName());
        supplier.setSupplierShortName(dto.getSupplierShortName());
        supplier.setTaxNo(dto.getTaxNo());
        supplier.setSupplierType(dto.getSupplierType());
        supplier.setRegionId(dto.getRegionId());
        supplier.setContactName(dto.getContactName());
        supplier.setContactPhone(dto.getContactPhone());
        supplier.setContactEmail(dto.getContactEmail());
        supplier.setAddress(dto.getAddress());
        supplier.setBankName(dto.getBankName());
        supplier.setBankAccount(dto.getBankAccount());
        supplier.setInvoiceTitle(dto.getInvoiceTitle());
        supplier.setDefaultPaymentTerm(dto.getDefaultPaymentTerm());
        supplier.setRatingLevel(dto.getRatingLevel());
        supplier.setOnboardingStatus(dto.getOnboardingStatus());
        supplier.setStatus(dto.getStatus());
        supplier.setBlacklistFlag(dto.getBlacklistFlag() == null ? 0 : dto.getBlacklistFlag());
        supplier.setDataIsolationFlag(dto.getDataIsolationFlag() == null ? 1 : dto.getDataIsolationFlag());
        supplier.setBlacklistReason(dto.getBlacklistReason());
        supplier.setEntryDate(dto.getEntryDate());
        supplier.setRemark(dto.getRemark());
    }

    private SupplierVO toVO(Supplier supplier) {
        SupplierVO vo = new SupplierVO();
        vo.setId(supplier.getId());
        vo.setSupplierCode(supplier.getSupplierCode());
        vo.setSupplierName(supplier.getSupplierName());
        vo.setSupplierShortName(supplier.getSupplierShortName());
        vo.setTaxNo(supplier.getTaxNo());
        vo.setSupplierType(supplier.getSupplierType());
        vo.setRegionId(supplier.getRegionId());
        vo.setContactName(supplier.getContactName());
        vo.setContactPhone(supplier.getContactPhone());
        vo.setContactEmail(supplier.getContactEmail());
        vo.setAddress(supplier.getAddress());
        vo.setBankName(supplier.getBankName());
        vo.setBankAccount(supplier.getBankAccount());
        vo.setInvoiceTitle(supplier.getInvoiceTitle());
        vo.setDefaultPaymentTerm(supplier.getDefaultPaymentTerm());
        vo.setRatingLevel(supplier.getRatingLevel());
        vo.setOnboardingStatus(supplier.getOnboardingStatus());
        vo.setStatus(supplier.getStatus());
        vo.setBlacklistFlag(supplier.getBlacklistFlag());
        vo.setDataIsolationFlag(supplier.getDataIsolationFlag());
        vo.setBlacklistReason(supplier.getBlacklistReason());
        vo.setBidSuspendedUntil(supplier.getBidSuspendedUntil());
        vo.setBidSuspendedReason(supplier.getBidSuspendedReason());
        vo.setEntryDate(supplier.getEntryDate());
        vo.setRemark(supplier.getRemark());
        vo.setCreatedAt(supplier.getCreatedAt());
        vo.setUpdatedAt(supplier.getUpdatedAt());
        Long qualificationCount = qualificationMapper.selectCount(new LambdaQueryWrapper<SupplierQualification>()
            .eq(SupplierQualification::getSupplierId, supplier.getId()));
        vo.setQualificationCount(qualificationCount);
        List<SysUser> linkedUsers = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getSupplierId, supplier.getId())
            .orderByAsc(SysUser::getUsername));
        vo.setLinkedUserCount((long) linkedUsers.size());
        vo.setLinkedUsernames(linkedUsers.stream().map(SysUser::getUsername).toList());
        return vo;
    }

    private Long linkedUserCount(Long supplierId) {
        return userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getSupplierId, supplierId));
    }

    private void ensureCanReadSupplier(Supplier supplier) {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser == null || DataScopeHelper.isSuperAdmin(authUser)) {
            return;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            if (!Objects.equals(authUser.getSupplierId(), supplier.getId())) {
                throw new BizException("FORBIDDEN", "Supplier account can only view its own data");
            }
            return;
        }
        String scope = DataScopeHelper.resolveDataScope(authUser);
        if (DataScopeHelper.SCOPE_REGION.equals(scope) && !Objects.equals(authUser.getRegionId(), supplier.getRegionId())) {
            throw new BizException("FORBIDDEN", "Current account cannot view suppliers in other regions");
        }
        if (DataScopeHelper.SCOPE_SELF.equals(scope) && !Objects.equals(authUser.getUserId(), supplier.getCreatedBy())) {
            throw new BizException("FORBIDDEN", "Current account cannot view this supplier");
        }
    }

    private void ensureCanWriteSupplier(Long targetRegionId) {
        AuthUser authUser = AuthContextHolder.get();
        if (authUser == null || DataScopeHelper.isSuperAdmin(authUser)) {
            return;
        }
        if (DataScopeHelper.isSupplierUser(authUser)) {
            throw new BizException("FORBIDDEN", "Supplier account cannot maintain supplier master data");
        }
        String scope = DataScopeHelper.resolveDataScope(authUser);
        if (DataScopeHelper.SCOPE_REGION.equals(scope) && !Objects.equals(authUser.getRegionId(), targetRegionId)) {
            throw new BizException("FORBIDDEN", "Current account cannot maintain suppliers in other regions");
        }
        if (DataScopeHelper.SCOPE_SELF.equals(scope)) {
            throw new BizException("FORBIDDEN", "Current account has no supplier maintenance permission");
        }
    }

    private String resolveSupplierCode(String supplierCode, Long excludeId) {
        if (StringUtils.hasText(supplierCode)) {
            return supplierCode.trim().toUpperCase(Locale.ROOT);
        }
        if (excludeId != null) {
            Supplier existing = baseMapper.selectById(excludeId);
            if (existing != null && StringUtils.hasText(existing.getSupplierCode())) {
                return existing.getSupplierCode();
            }
        }
        String base = "SUP" + LocalDateTime.now().format(SUPPLIER_CODE_FORMATTER);
        String candidate = base;
        int sequence = 1;
        while (existsSupplierCode(candidate, excludeId)) {
            candidate = base + String.format(Locale.ROOT, "%02d", sequence++);
        }
        return candidate;
    }

    private boolean existsSupplierCode(String supplierCode, Long excludeId) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>().eq(Supplier::getSupplierCode, supplierCode);
        if (excludeId != null) {
            wrapper.ne(Supplier::getId, excludeId);
        }
        return baseMapper.selectCount(wrapper) > 0;
    }
}
