SET NAMES utf8mb4;

INSERT INTO procurement_setting
  (setting_key, setting_value, setting_group, setting_label, value_type, sort_no, remark, created_by, updated_by, is_deleted, version)
VALUES
  ('feishu_app_id', '', 'FEISHU', '飞书 App ID', 'TEXT', 10, '企业自建应用 App ID；留空时使用服务器环境变量 FEISHU_APP_ID', 0, 0, 0, 0),
  ('feishu_app_secret', '', 'FEISHU', '飞书 App Secret', 'PASSWORD', 20, '企业自建应用 App Secret；留空时使用服务器环境变量 FEISHU_APP_SECRET', 0, 0, 0, 0),
  ('feishu_approval_card_enabled', '', 'FEISHU', '启用审批卡片', 'BOOLEAN', 30, '开启后，本地心跳会扫描待审批任务并推送飞书交互卡片；留空时使用环境变量', 0, 0, 0, 0),
  ('feishu_approval_card_heartbeat_enabled', '', 'FEISHU', '启用本地心跳', 'BOOLEAN', 40, '开启后，由系统定时扫描待审批任务并主动推送卡片；留空时使用环境变量', 0, 0, 0, 0),
  ('feishu_approval_card_long_connection_enabled', '', 'FEISHU', '启用长连接回调', 'BOOLEAN', 50, '开启后，系统通过飞书长连接接收卡片按钮回调；留空时使用环境变量', 0, 0, 0, 0),
  ('feishu_approval_card_verification_token', '', 'FEISHU', '回调 Verification Token', 'PASSWORD', 60, '飞书开放平台事件与回调配置中的 Verification Token；留空时使用环境变量', 0, 0, 0, 0),
  ('feishu_approval_card_encrypt_key', '', 'FEISHU', '回调 Encrypt Key', 'PASSWORD', 70, '飞书开放平台事件与回调配置中的 Encrypt Key，未启用加密可留空', 0, 0, 0, 0),
  ('feishu_approval_card_app_base_url', '', 'FEISHU', '系统访问基址', 'TEXT', 80, '系统可访问基址，例如 http://100.125.220.73:10086；用于图片中转和未配置系统处理入口地址时的卡片兜底', 0, 0, 0, 0),
  ('feishu_approval_card_system_action_url', 'https://mercy-sheep-essentials-genius.trycloudflare.com/login?redirect=/workbench', 'FEISHU', '系统处理入口地址', 'TEXT', 85, '审批卡片中“系统处理”按钮打开的完整地址，可填写登录后跳转工作台的链接', 0, 0, 0, 0),
  ('feishu_approval_card_receive_id_type', 'email', 'FEISHU', '消息接收 ID 类型', 'TEXT', 90, '默认 email，使用系统用户邮箱发送；也可改为 open_id、user_id 或 union_id', 0, 0, 0, 0),
  ('feishu_approval_card_detail_item_limit', '', 'FEISHU', '卡片明细展示条数', 'NUMBER', 100, '卡片内最多展示的采购明细数量；留空时默认 8', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  setting_group = VALUES(setting_group),
  setting_label = VALUES(setting_label),
  value_type = VALUES(value_type),
  sort_no = VALUES(sort_no),
  remark = VALUES(remark),
  updated_at = CURRENT_TIMESTAMP;
