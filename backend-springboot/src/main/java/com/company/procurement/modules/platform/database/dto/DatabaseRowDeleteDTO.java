package com.company.procurement.modules.platform.database.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
public class DatabaseRowDeleteDTO {

    @NotEmpty(message = "Primary key values cannot be empty")
    private Map<String, Object> keyValues;
}
