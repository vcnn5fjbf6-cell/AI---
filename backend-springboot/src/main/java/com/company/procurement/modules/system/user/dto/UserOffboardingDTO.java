package com.company.procurement.modules.system.user.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UserOffboardingDTO {

    @Valid
    private List<UserOffboardingReplacementDTO> replacements = new ArrayList<>();
}
