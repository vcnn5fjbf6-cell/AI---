package com.company.procurement.modules.platform.feedback.dto;

import com.company.procurement.common.dto.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PlatformFeedbackPageQueryDTO extends PageQuery {

    private String category;

    private String status;

    private String keyword;
}
