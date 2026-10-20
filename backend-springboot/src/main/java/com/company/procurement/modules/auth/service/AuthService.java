package com.company.procurement.modules.auth.service;

import com.company.procurement.modules.auth.dto.AuthLoginDTO;
import com.company.procurement.modules.auth.dto.AuthPasswordChangeDTO;
import com.company.procurement.modules.auth.dto.FeishuCallbackDTO;
import com.company.procurement.modules.auth.vo.AuthLoginVO;
import com.company.procurement.modules.auth.vo.AuthUserVO;
import com.company.procurement.modules.auth.vo.FeishuLoginUrlVO;

public interface AuthService {

    AuthLoginVO login(AuthLoginDTO dto, String clientIp);

    FeishuLoginUrlVO feishuLoginUrl(String redirectUri);

    AuthLoginVO feishuCallback(FeishuCallbackDTO dto, String clientIp);

    AuthUserVO currentUser();

    void changePassword(AuthPasswordChangeDTO dto);

    void logout();
}
