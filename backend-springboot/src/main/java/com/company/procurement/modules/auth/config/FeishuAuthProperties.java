package com.company.procurement.modules.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "feishu.auth")
public class FeishuAuthProperties {

    private boolean enabled = false;

    private String appId;

    private String appSecret;

    private String redirectUri;

    private String authorizeUrl = "https://open.feishu.cn/open-apis/authen/v1/index";

    private String appAccessTokenUrl = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";

    private String tenantAccessTokenUrl = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";

    private String accessTokenUrl = "https://open.feishu.cn/open-apis/authen/v1/access_token";

    private String userInfoUrl = "https://open.feishu.cn/open-apis/authen/v1/user_info";

    private String userDetailUrl = "https://open.feishu.cn/open-apis/contact/v3/users/%s";

    private String userBatchGetIdUrl = "https://open.feishu.cn/open-apis/contact/v3/users/batch_get_id";

    private String departmentChildrenUrl = "https://open.feishu.cn/open-apis/contact/v3/departments/%s/children";

    private String departmentSearchUrl = "https://open.feishu.cn/open-apis/contact/v3/departments/search";

    private String departmentUsersUrl = "https://open.feishu.cn/open-apis/contact/v3/users/find_by_department";

    private String rootDepartmentId = "0";

    private String departmentIdType = "department_id";

    private String userIdType = "user_id";

    private int directorySyncPageSize = 50;

    private long stateTtlSeconds = 600L;
}
