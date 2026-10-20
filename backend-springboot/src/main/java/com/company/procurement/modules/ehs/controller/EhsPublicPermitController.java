package com.company.procurement.modules.ehs.controller;

import com.company.procurement.common.result.Result;
import com.company.procurement.modules.ehs.dto.EhsPermitApprovalDTO;
import com.company.procurement.modules.ehs.dto.EhsPermitCloseDTO;
import com.company.procurement.modules.ehs.dto.EhsPermitSampleDTO;
import com.company.procurement.modules.ehs.dto.EhsPublicPermitSaveDTO;
import com.company.procurement.modules.ehs.service.EhsDepartmentOptionService;
import com.company.procurement.modules.ehs.service.EhsPermitService;
import com.company.procurement.modules.ehs.service.EhsPublicRateLimitService;
import com.company.procurement.modules.ehs.vo.EhsDepartmentOptionVO;
import com.company.procurement.modules.ehs.vo.EhsPublicPermitCreatedVO;
import com.company.procurement.modules.ehs.vo.EhsPublicPermitVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/public/ehs")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ehs", name = "public-enabled", havingValue = "true")
public class EhsPublicPermitController {

    private static final String TOKEN_HEADER = "X-EHS-Public-Token";

    private final EhsPermitService permitService;
    private final EhsDepartmentOptionService departmentOptionService;
    private final EhsPublicRateLimitService rateLimitService;

    @GetMapping("/departments")
    public Result<List<EhsDepartmentOptionVO>> departments(HttpServletRequest request) {
        rateLimitService.check(request.getRemoteAddr(), "departments", 120, Duration.ofMinutes(1));
        return Result.ok(departmentOptionService.listEnabledOptions());
    }

    @PostMapping("/permits")
    public Result<EhsPublicPermitCreatedVO> create(@RequestBody @Valid EhsPublicPermitSaveDTO dto,
                                                   HttpServletRequest request) {
        rateLimitService.check(request.getRemoteAddr(), "create", 5, Duration.ofMinutes(10));
        return Result.ok(permitService.createPublic(dto));
    }

    @GetMapping("/permit")
    public Result<EhsPublicPermitVO> detail(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                            HttpServletRequest request) {
        rateLimitService.check(request.getRemoteAddr(), "detail", 120, Duration.ofMinutes(1));
        return Result.ok(permitService.publicDetail(token));
    }

    @PutMapping("/permits/{id:\\d+}")
    public Result<Void> update(@PathVariable Long id,
                               @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                               @RequestBody @Valid EhsPublicPermitSaveDTO dto,
                               HttpServletRequest request) {
        rateLimitService.check(request.getRemoteAddr(), "update", 20, Duration.ofMinutes(10));
        permitService.updatePublic(token, id, dto);
        return Result.ok();
    }

    @PostMapping("/permits/{id:\\d+}/submit")
    public Result<Void> submit(@PathVariable Long id,
                               @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                               HttpServletRequest request) {
        rateLimitService.check(request.getRemoteAddr(), "submit", 20, Duration.ofMinutes(10));
        permitService.submitPublic(token, id);
        return Result.ok();
    }

    @PostMapping("/permits/{id:\\d+}/start")
    public Result<Void> start(@PathVariable Long id,
                              @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                              HttpServletRequest request) {
        rateLimitService.check(request.getRemoteAddr(), "start", 20, Duration.ofMinutes(10));
        permitService.startPublic(token, id);
        return Result.ok();
    }

    @PostMapping("/permits/{id:\\d+}/samples")
    public Result<Void> addSample(@PathVariable Long id,
                                  @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                  @RequestBody @Valid EhsPermitSampleDTO dto,
                                  HttpServletRequest request) {
        rateLimitService.check(request.getRemoteAddr(), "sample", 30, Duration.ofMinutes(10));
        permitService.addSamplePublic(token, id, dto);
        return Result.ok();
    }

    @PostMapping("/permits/{id:\\d+}/close")
    public Result<Void> close(@PathVariable Long id,
                              @RequestHeader(value = TOKEN_HEADER, required = false) String token,
                              @RequestBody @Valid EhsPermitCloseDTO dto,
                              HttpServletRequest request) {
        rateLimitService.check(request.getRemoteAddr(), "close", 20, Duration.ofMinutes(10));
        permitService.closePublic(token, id, dto);
        return Result.ok();
    }

    @GetMapping("/review")
    public Result<EhsPublicPermitVO> review(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                            HttpServletRequest request) {
        rateLimitService.check(request.getRemoteAddr(), "review", 120, Duration.ofMinutes(1));
        return Result.ok(permitService.publicReview(token));
    }

    @PostMapping("/review/approval")
    public Result<Void> approve(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                @RequestBody @Valid EhsPermitApprovalDTO dto,
                                HttpServletRequest request) {
        rateLimitService.check(request.getRemoteAddr(), "approve", 20, Duration.ofMinutes(10));
        permitService.approvePublic(token, dto);
        return Result.ok();
    }
}
