package com.company.procurement.modules.procurement.integration.shopping.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ShoppingSearchResultVO {

    private String keyword;

    private String category;

    private String summary;

    private Boolean mockData = true;

    private List<ShoppingProductVO> products = new ArrayList<>();
}
