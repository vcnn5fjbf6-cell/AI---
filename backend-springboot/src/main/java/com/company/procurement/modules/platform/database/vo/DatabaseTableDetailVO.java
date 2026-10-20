package com.company.procurement.modules.platform.database.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DatabaseTableDetailVO {

    private DatabaseTableVO table;

    private List<DatabaseColumnVO> columns = new ArrayList<>();

    private List<String> primaryKeys = new ArrayList<>();
}
