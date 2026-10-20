package com.company.procurement.common.security;

import lombok.Data;

@Data
public class PermissionApi {

    private String permCode;

    private String path;

    private String httpMethod;
}
