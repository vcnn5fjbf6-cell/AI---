package com.company.procurement.modules.procurement.settings.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.procurement.modules.procurement.settings.entity.ProcurementDepartmentLimit;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProcurementDepartmentLimitMapper extends BaseMapper<ProcurementDepartmentLimit> {

    @Delete("DELETE FROM procurement_department_limit WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
