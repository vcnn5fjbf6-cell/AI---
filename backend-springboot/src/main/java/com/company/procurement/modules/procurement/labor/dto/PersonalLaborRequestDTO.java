package com.company.procurement.modules.procurement.labor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PersonalLaborRequestDTO {

    private String requestTitle;
    private String deptCode;
    private String deptName;
    private String budgetCode;
    private String receivingCity;
    private String reason;
    private LocalDate needDate;
    private Boolean emergency;

    @Valid
    @NotEmpty(message = "请选择要申领的劳保用品")
    private List<PersonalLaborRequestItemDTO> items;
}
