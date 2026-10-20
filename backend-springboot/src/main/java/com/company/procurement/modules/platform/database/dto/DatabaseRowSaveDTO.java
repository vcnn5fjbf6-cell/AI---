package com.company.procurement.modules.platform.database.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class DatabaseRowSaveDTO {

    @NotEmpty(message = "Row values cannot be empty")
    private Map<String, Object> values;
}
