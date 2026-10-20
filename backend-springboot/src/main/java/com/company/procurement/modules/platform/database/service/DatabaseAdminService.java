package com.company.procurement.modules.platform.database.service;

import com.company.procurement.common.result.PageResult;
import com.company.procurement.modules.platform.database.dto.DatabaseRowDeleteDTO;
import com.company.procurement.modules.platform.database.dto.DatabaseRowSaveDTO;
import com.company.procurement.modules.platform.database.dto.DatabaseRowUpdateDTO;
import com.company.procurement.modules.platform.database.dto.DatabaseTablePageQueryDTO;
import com.company.procurement.modules.platform.database.vo.DatabaseTableDetailVO;
import com.company.procurement.modules.platform.database.vo.DatabaseTableVO;

import java.util.List;
import java.util.Map;

public interface DatabaseAdminService {

    List<DatabaseTableVO> listTables(String keyword);

    DatabaseTableDetailVO detail(String tableName);

    PageResult<Map<String, Object>> pageRows(String tableName, DatabaseTablePageQueryDTO queryDTO);

    void createRow(String tableName, DatabaseRowSaveDTO dto);

    void updateRow(String tableName, DatabaseRowUpdateDTO dto);

    void deleteRow(String tableName, DatabaseRowDeleteDTO dto);
}
