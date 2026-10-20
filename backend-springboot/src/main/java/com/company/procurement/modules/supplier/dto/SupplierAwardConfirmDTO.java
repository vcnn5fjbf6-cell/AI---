package com.company.procurement.modules.supplier.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupplierAwardConfirmDTO {

    @Size(max = 1000)
    private String confirmRemark;
}
