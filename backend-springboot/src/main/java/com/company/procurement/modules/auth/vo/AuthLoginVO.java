package com.company.procurement.modules.auth.vo;

import java.util.List;

import lombok.Data;

@Data
public class AuthLoginVO {

    private String token;

    private String tokenType;

    private Long expiresIn;

    private AuthUserVO user;

    private Boolean requiresUserSelection;

    private List<AuthLoginCandidateVO> candidates;
}
