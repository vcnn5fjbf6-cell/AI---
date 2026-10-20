SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS hr_update_reminder_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  `year_month` VARCHAR(7) NOT NULL COMMENT '提醒月份 YYYY-MM',
  user_id BIGINT NOT NULL COMMENT '楼长用户ID',
  real_name VARCHAR(64) NULL COMMENT '楼长姓名',
  building VARCHAR(128) NULL COMMENT '楼栋/部门',
  remind_date DATE NOT NULL COMMENT '提醒日期',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/LATE',
  send_status VARCHAR(32) NULL COMMENT 'SENT/FAILED',
  send_message VARCHAR(500) NULL COMMENT '发送结果',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_hr_remind_user_month_date (user_id, `year_month`, remind_date),
  KEY idx_hr_remind_month_status (`year_month`, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人力资源楼长更新提醒日志';
