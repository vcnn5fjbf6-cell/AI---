package com.company.procurement.modules.integration.feishu.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.procurement.modules.integration.feishu.service.FeishuApprovalCardService;
import com.company.procurement.modules.integration.feishu.vo.FeishuApprovalCardActionResult;
import com.company.procurement.modules.system.user.entity.SysUser;
import com.company.procurement.modules.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HrUpdateReminderScheduler {

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SysUserMapper userMapper;
    private final FeishuApprovalCardService approvalCardService;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Shanghai")
    public void runMonthlyLeaderReminder() {
        LocalDate today = LocalDate.now();
        if (today.getDayOfMonth() <= 10) {
            remindFloorLeaders(today);
        } else {
            markPendingAsLate(today);
        }
    }

    private void remindFloorLeaders(LocalDate today) {
        List<SysUser> leaders = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
            .eq(SysUser::getUserType, "FLOOR_LEADER")
            .eq(SysUser::getStatus, 1)
            .eq(SysUser::getIsDeleted, 0));
        String yearMonth = today.format(YEAR_MONTH_FORMATTER);
        for (SysUser leader : leaders) {
            try {
                Integer sentCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM hr_update_reminder_log WHERE user_id = ? AND `year_month` = ? AND remind_date = ?",
                    Integer.class,
                    leader.getId(),
                    yearMonth,
                    today.toString()
                );
                if (sentCount != null && sentCount > 0) {
                    continue;
                }
                String leaderName = StringUtils.hasText(leader.getRealName()) ? leader.getRealName() : leader.getUsername();
                String building = StringUtils.hasText(leader.getDeptName()) ? leader.getDeptName() : "-";
                String title = "每月人员信息更新提醒";
                String content = leaderName + "，请在本月10日前完成楼栋人员信息更新（入职/离职/岗位/联系方式）。当前日期：" + today;
                FeishuApprovalCardActionResult result = approvalCardService.sendBusinessNotification(leader.getId(), title, content);
                boolean success = result != null && result.isSuccess();
                jdbcTemplate.update(
                    "INSERT INTO hr_update_reminder_log (`year_month`, user_id, real_name, building, remind_date, status, send_status, send_message) "
                        + "VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)",
                    yearMonth,
                    leader.getId(),
                    leaderName,
                    building,
                    today.toString(),
                    success ? "SENT" : "FAILED",
                    success ? "success" : "发送失败"
                );
            } catch (RuntimeException ex) {
                log.warn("HR update reminder send failed, userId={}", leader.getId(), ex);
            }
        }
    }

    private void markPendingAsLate(LocalDate today) {
        String yearMonth = today.format(YEAR_MONTH_FORMATTER);
        int updated = jdbcTemplate.update(
            "UPDATE hr_update_reminder_log SET status = 'LATE', updated_at = NOW() WHERE `year_month` = ? AND status = 'PENDING'",
            yearMonth
        );
        if (updated > 0) {
            log.info("Marked {} HR update reminders as late for {}", updated, yearMonth);
        }
    }
}
