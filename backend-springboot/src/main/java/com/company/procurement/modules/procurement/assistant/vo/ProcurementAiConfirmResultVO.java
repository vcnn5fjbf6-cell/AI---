package com.company.procurement.modules.procurement.assistant.vo;

import com.company.procurement.modules.procurement.request.vo.PurchaseRequestVO;
import lombok.Data;

@Data
public class ProcurementAiConfirmResultVO {

    private Long sessionId;
    private String sessionNo;
    private Integer candidateNo;
    private Long purchaseRequestId;
    private String purchaseRequestNo;
    private String status;
    private PurchaseRequestVO request;
}
