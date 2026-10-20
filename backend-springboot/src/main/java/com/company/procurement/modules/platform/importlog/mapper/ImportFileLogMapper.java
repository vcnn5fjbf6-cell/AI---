package com.company.procurement.modules.platform.importlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.procurement.modules.platform.importlog.entity.ImportFileLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ImportFileLogMapper extends BaseMapper<ImportFileLog> {

    @Delete("DELETE FROM platform_import_file_log WHERE occurred_at < #{cutoff}")
    int physicalDeleteBefore(@Param("cutoff") LocalDateTime cutoff);
}
