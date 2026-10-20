package com.company.procurement.modules.procurement.monthly.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MonthlyAggregateResultVO {

    private String planMonth;
    private int demandCount;
    private int requestCount;
    private List<Long> purchaseRequestIds = new ArrayList<>();
}
