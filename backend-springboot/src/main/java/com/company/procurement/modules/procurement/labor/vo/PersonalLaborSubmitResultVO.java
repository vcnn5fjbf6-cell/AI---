package com.company.procurement.modules.procurement.labor.vo;

import lombok.Data;

@Data
public class PersonalLaborSubmitResultVO {

    private Long purchaseRequestId;
    private String purchaseRequestNo;
    private String nextNodeName;
    private String message;
}
