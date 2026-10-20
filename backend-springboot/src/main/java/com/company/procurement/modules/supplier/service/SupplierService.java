package com.company.procurement.modules.supplier.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.supplier.dto.SupplierBanDTO;
import com.company.procurement.modules.supplier.dto.SupplierCreateDTO;
import com.company.procurement.modules.supplier.dto.SupplierPageQueryDTO;
import com.company.procurement.modules.supplier.dto.SupplierUpdateDTO;
import com.company.procurement.modules.supplier.entity.Supplier;
import com.company.procurement.modules.supplier.vo.SupplierVO;

import java.util.List;

public interface SupplierService extends IService<Supplier> {

    PageResult<SupplierVO> page(SupplierPageQueryDTO queryDTO);

    List<SupplierVO> list(SupplierPageQueryDTO queryDTO);

    SupplierVO detail(Long id);

    Long create(SupplierCreateDTO dto);

    void update(Long id, SupplierUpdateDTO dto);

    void releaseBiddingSuspension(Long id);

    void ban(Long id, SupplierBanDTO dto);

    void unban(Long id);

    void remove(Long id);
}
