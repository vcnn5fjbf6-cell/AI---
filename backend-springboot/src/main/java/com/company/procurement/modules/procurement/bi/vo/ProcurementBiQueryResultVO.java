package com.company.procurement.modules.procurement.bi.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ProcurementBiQueryResultVO {

    private String datasetCode;

    private List<ColumnVO> columns = new ArrayList<>();

    private List<Map<String, Object>> rows = new ArrayList<>();

    private Map<String, Object> summary = new LinkedHashMap<>();

    @Data
    public static class ColumnVO {
        private String code;
        private String label;
        private String role;
        private String dataType;
        private String semanticType;
        private String unit;
        private String aggregation;
    }
}
