package com.company.procurement.modules.procurement.integration.shopping.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ShoppingSearchDTO {

    @NotBlank(message = "keyword must not be blank")
    private String keyword;

    private String category;

    private Integer maxResults = 5;

    private List<String> platforms;
}
