SET NAMES utf8mb4;

ALTER TABLE approval_record
  MODIFY COLUMN approver_user_id BIGINT NULL COMMENT '审批人ID';
