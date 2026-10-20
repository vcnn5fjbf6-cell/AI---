package com.company.procurement.modules.procurement.integration.shopping.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ShoppingProductVO {

    private String itemName;

    private String providerCode;

    private String skuId;

    private String specModel;

    private String unit;

    private BigDecimal qty;

    private BigDecimal unitPrice;

    private String ecommercePlatform;

    private String supplierName;

    private String stockStatus;

    private String deliveryLeadTime;

    private String ecommerceUrl;

    private String imageUrl;

    private String purchaseReason;

    private BigDecimal score;
}
