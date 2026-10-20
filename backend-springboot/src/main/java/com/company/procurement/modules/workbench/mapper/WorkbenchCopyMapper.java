package com.company.procurement.modules.workbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.procurement.modules.workbench.entity.WorkbenchCopy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface WorkbenchCopyMapper extends BaseMapper<WorkbenchCopy> {

    @Delete("DELETE FROM workbench_copy WHERE source_type = #{sourceType} AND source_id = #{sourceId}")
    int physicalDeleteBySource(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);
}
