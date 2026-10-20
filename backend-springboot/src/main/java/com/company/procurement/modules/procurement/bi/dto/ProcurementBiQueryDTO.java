package com.company.procurement.modules.procurement.bi.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProcurementBiQueryDTO {

    private String datasetCode;

    private Boolean detail;

    private List<String> fields = new ArrayList<>();

    private List<String> dimensions = new ArrayList<>();

    private List<MetricDTO> metrics = new ArrayList<>();

    private List<FilterDTO> filters = new ArrayList<>();

    private List<SortDTO> sorts = new ArrayList<>();

    private Integer limit;

    @Data
    public static class MetricDTO {
        private String fieldCode;
        private String aggregation;
    }

    @Data
    public static class FilterDTO {
        private String fieldCode;
        private String operator;
        private Object value;
        private List<Object> values = new ArrayList<>();
    }

    @Data
    public static class SortDTO {
        private String fieldCode;
        private String direction;
    }
}
