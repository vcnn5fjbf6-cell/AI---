package com.company.procurement.modules.procurement.bi.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProcurementBiDatasetVO {

    private String datasetCode;

    private String datasetName;

    private String description;

    private List<FieldVO> fields = new ArrayList<>();

    @Data
    public static class FieldVO {
        private String fieldCode;
        private String fieldName;
        private String dataType;
        private String semanticType;
        private String role;
        private String unit;
        private String defaultAggregation;
        private boolean filterable;
        private boolean groupable;
        private boolean sortable;
    }
}
