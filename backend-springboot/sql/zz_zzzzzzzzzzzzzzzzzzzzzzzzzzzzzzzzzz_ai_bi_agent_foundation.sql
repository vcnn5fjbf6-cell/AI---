CREATE TABLE IF NOT EXISTS bi_dataset (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  dataset_code VARCHAR(64) NOT NULL COMMENT '数据集编码',
  dataset_name VARCHAR(128) NOT NULL COMMENT '数据集中文名称',
  source_table VARCHAR(128) NOT NULL COMMENT '来源物理表',
  biz_domain VARCHAR(64) NOT NULL DEFAULT 'PROCUREMENT' COMMENT '业务域',
  visibility_scope VARCHAR(32) NOT NULL DEFAULT 'INTERNAL' COMMENT '展示范围 INTERNAL/SUPPLIER/ADMIN',
  refresh_mode VARCHAR(32) NOT NULL DEFAULT 'REALTIME' COMMENT '刷新方式 REALTIME/SCHEDULED',
  description VARCHAR(1000) NULL COMMENT '说明',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_bi_dataset_code (dataset_code),
  KEY idx_bi_dataset_domain (biz_domain, enabled, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI数据集定义';

CREATE TABLE IF NOT EXISTS bi_dataset_field (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  dataset_id BIGINT NOT NULL COMMENT '数据集ID',
  field_code VARCHAR(64) NOT NULL COMMENT '字段编码',
  field_name VARCHAR(128) NOT NULL COMMENT '字段中文名称',
  physical_column VARCHAR(128) NOT NULL COMMENT '来源字段',
  data_type VARCHAR(32) NOT NULL COMMENT '字段类型 STRING/NUMBER/DATE/DATETIME/BOOLEAN',
  semantic_type VARCHAR(64) NULL COMMENT '语义类型',
  unit VARCHAR(32) NULL COMMENT '单位',
  default_aggregation VARCHAR(32) NULL COMMENT '默认聚合 SUM/COUNT/AVG/MIN/MAX',
  filterable TINYINT NOT NULL DEFAULT 1 COMMENT '是否可筛选',
  groupable TINYINT NOT NULL DEFAULT 1 COMMENT '是否可分组',
  sortable TINYINT NOT NULL DEFAULT 1 COMMENT '是否可排序',
  sensitive_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否敏感字段',
  description VARCHAR(1000) NULL COMMENT '说明',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_bi_field_dataset_code (dataset_id, field_code),
  KEY idx_bi_field_dataset (dataset_id, sort_no, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI数据集字段字典';

CREATE TABLE IF NOT EXISTS bi_dataset_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  relation_code VARCHAR(128) NOT NULL COMMENT '关系编码',
  source_dataset_code VARCHAR(64) NOT NULL COMMENT '源数据集编码',
  target_dataset_code VARCHAR(64) NOT NULL COMMENT '目标数据集编码',
  relation_type VARCHAR(32) NOT NULL DEFAULT 'LEFT_JOIN' COMMENT '关系类型',
  join_condition VARCHAR(1000) NOT NULL COMMENT '关联条件',
  description VARCHAR(1000) NULL COMMENT '说明',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_bi_relation_code (relation_code),
  KEY idx_bi_relation_source (source_dataset_code, enabled, is_deleted),
  KEY idx_bi_relation_target (target_dataset_code, enabled, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI数据集关联关系';

CREATE TABLE IF NOT EXISTS bi_metric (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  metric_code VARCHAR(64) NOT NULL COMMENT '指标编码',
  metric_name VARCHAR(128) NOT NULL COMMENT '指标中文名称',
  dataset_code VARCHAR(64) NOT NULL COMMENT '所属数据集编码',
  metric_type VARCHAR(32) NOT NULL DEFAULT 'MEASURE' COMMENT '指标类型 MEASURE/RATIO/COUNT',
  expression_sql TEXT NOT NULL COMMENT '计算表达式',
  unit VARCHAR(32) NULL COMMENT '单位',
  dimension_json JSON NULL COMMENT '默认维度配置',
  description VARCHAR(1000) NULL COMMENT '说明',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_bi_metric_code (metric_code),
  KEY idx_bi_metric_dataset (dataset_code, enabled, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI指标定义';

CREATE TABLE IF NOT EXISTS bi_dashboard (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  dashboard_code VARCHAR(64) NOT NULL COMMENT '看板编码',
  dashboard_name VARCHAR(128) NOT NULL COMMENT '看板中文名称',
  route_path VARCHAR(255) NULL COMMENT '页面路径',
  owner_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属用户，0表示公共看板',
  layout_schema JSON NULL COMMENT '默认布局配置',
  description VARCHAR(1000) NULL COMMENT '说明',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/DRAFT/DISABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_bi_dashboard_code_owner (dashboard_code, owner_user_id),
  KEY idx_bi_dashboard_status (status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI看板定义';

CREATE TABLE IF NOT EXISTS bi_dashboard_widget (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  dashboard_code VARCHAR(64) NOT NULL COMMENT '看板编码',
  widget_code VARCHAR(64) NOT NULL COMMENT '组件编码',
  widget_name VARCHAR(128) NOT NULL COMMENT '组件中文名称',
  widget_type VARCHAR(32) NOT NULL COMMENT '组件类型 METRIC/TABLE/CHART/FILTER',
  dataset_code VARCHAR(64) NULL COMMENT '数据集编码',
  metric_codes JSON NULL COMMENT '指标编码列表',
  query_config JSON NULL COMMENT '查询配置',
  layout_config JSON NULL COMMENT '布局配置',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_bi_widget_dashboard_code (dashboard_code, widget_code),
  KEY idx_bi_widget_dashboard (dashboard_code, enabled, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI看板组件定义';

CREATE TABLE IF NOT EXISTS bi_dashboard_filter (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  dashboard_code VARCHAR(64) NOT NULL COMMENT '看板编码',
  filter_code VARCHAR(64) NOT NULL COMMENT '筛选器编码',
  filter_name VARCHAR(128) NOT NULL COMMENT '筛选器中文名称',
  dataset_code VARCHAR(64) NOT NULL COMMENT '数据集编码',
  field_code VARCHAR(64) NOT NULL COMMENT '字段编码',
  filter_type VARCHAR(32) NOT NULL DEFAULT 'SELECT' COMMENT '筛选类型 SELECT/DATE_RANGE/INPUT',
  default_value VARCHAR(500) NULL COMMENT '默认值',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_bi_filter_dashboard_code (dashboard_code, filter_code),
  KEY idx_bi_filter_dashboard (dashboard_code, enabled, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI看板筛选器定义';

CREATE TABLE IF NOT EXISTS bi_saved_view (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  view_code VARCHAR(64) NOT NULL COMMENT '视图编码',
  view_name VARCHAR(128) NOT NULL COMMENT '视图中文名称',
  owner_user_id BIGINT NOT NULL COMMENT '所属用户ID',
  dataset_code VARCHAR(64) NOT NULL COMMENT '主数据集编码',
  view_config JSON NOT NULL COMMENT '字段、筛选、排序和图表配置',
  description VARCHAR(1000) NULL COMMENT '说明',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/DISABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_bi_view_owner_code (owner_user_id, view_code),
  KEY idx_bi_view_dataset (dataset_code, status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI用户自定义视图';

CREATE TABLE IF NOT EXISTS bi_share_scope (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  target_type VARCHAR(32) NOT NULL COMMENT '共享对象 DASHBOARD/VIEW',
  target_code VARCHAR(64) NOT NULL COMMENT '共享对象编码',
  scope_type VARCHAR(32) NOT NULL COMMENT '范围类型 USER/ROLE/APP/PUBLIC',
  scope_value VARCHAR(128) NOT NULL COMMENT '范围值',
  permission_level VARCHAR(32) NOT NULL DEFAULT 'READ' COMMENT '权限 READ/EDIT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_bi_share_scope (target_type, target_code, scope_type, scope_value),
  KEY idx_bi_share_target (target_type, target_code, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BI看板和视图共享范围';

CREATE TABLE IF NOT EXISTS ai_question_answer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  question_hash VARCHAR(128) NOT NULL COMMENT '标准问题哈希',
  canonical_question VARCHAR(1000) NOT NULL COMMENT '标准问题',
  answer_text MEDIUMTEXT NOT NULL COMMENT '标准答案',
  answer_type VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT '答案类型 TEXT/API/DASHBOARD',
  biz_domain VARCHAR(64) NOT NULL DEFAULT 'PROCUREMENT' COMMENT '业务域',
  intent_code VARCHAR(64) NULL COMMENT '意图编码',
  source_type VARCHAR(64) NULL COMMENT '来源类型',
  source_ref VARCHAR(255) NULL COMMENT '来源引用',
  hit_count BIGINT NOT NULL DEFAULT 0 COMMENT '命中次数',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_ai_qa_hash (question_hash),
  KEY idx_ai_qa_domain (biz_domain, intent_code, enabled, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI常见问题标准答案库';

CREATE TABLE IF NOT EXISTS ai_question_variant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  qa_id BIGINT NOT NULL COMMENT '标准答案ID',
  question_hash VARCHAR(128) NOT NULL COMMENT '相似问法哈希',
  question_text VARCHAR(1000) NOT NULL COMMENT '相似问法',
  normalized_text VARCHAR(1000) NULL COMMENT '归一化文本',
  similarity_threshold DECIMAL(6,4) NOT NULL DEFAULT 0.8500 COMMENT '相似度阈值',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_ai_variant_hash (question_hash),
  KEY idx_ai_variant_qa (qa_id, enabled, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI常见问题相似问法';

CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  chunk_key VARCHAR(128) NOT NULL COMMENT '知识片段唯一键',
  biz_domain VARCHAR(64) NOT NULL DEFAULT 'PROCUREMENT' COMMENT '业务域',
  source_type VARCHAR(64) NOT NULL COMMENT '来源类型',
  source_id VARCHAR(128) NULL COMMENT '来源ID',
  title VARCHAR(255) NOT NULL COMMENT '标题',
  content MEDIUMTEXT NOT NULL COMMENT '正文内容',
  tags_json JSON NULL COMMENT '标签',
  content_version VARCHAR(64) NULL COMMENT '内容版本',
  vector_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '向量状态 PENDING/SYNCED/FAILED',
  vector_id VARCHAR(128) NULL COMMENT '向量ID',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_ai_chunk_key (chunk_key),
  KEY idx_ai_chunk_domain (biz_domain, source_type, enabled, is_deleted),
  KEY idx_ai_chunk_vector (vector_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识片段';

CREATE TABLE IF NOT EXISTS ai_prompt_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
  template_name VARCHAR(128) NOT NULL COMMENT '模板中文名称',
  scenario_code VARCHAR(64) NOT NULL COMMENT '场景编码',
  system_prompt MEDIUMTEXT NULL COMMENT '系统提示词',
  user_prompt_template MEDIUMTEXT NOT NULL COMMENT '用户提示词模板',
  model_config JSON NULL COMMENT '模型参数配置',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_ai_prompt_template_code (template_code),
  KEY idx_ai_prompt_scenario (scenario_code, enabled, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI提示词模板';

CREATE TABLE IF NOT EXISTS ai_agent_tool (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  tool_code VARCHAR(64) NOT NULL COMMENT '工具编码',
  tool_name VARCHAR(128) NOT NULL COMMENT '工具中文名称',
  tool_type VARCHAR(32) NOT NULL DEFAULT 'HTTP' COMMENT '工具类型 HTTP/SQL/WORKFLOW',
  endpoint VARCHAR(255) NULL COMMENT '调用地址或内部接口',
  request_schema JSON NULL COMMENT '请求结构',
  response_schema JSON NULL COMMENT '响应结构',
  auth_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '认证方式 SYSTEM/USER/NONE',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_ai_tool_code (tool_code),
  KEY idx_ai_tool_enabled (enabled, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能体工具注册';

CREATE TABLE IF NOT EXISTS ai_agent_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  session_key VARCHAR(128) NOT NULL COMMENT '会话唯一键',
  user_id BIGINT NULL COMMENT '用户ID',
  biz_domain VARCHAR(64) NOT NULL DEFAULT 'PROCUREMENT' COMMENT '业务域',
  title VARCHAR(255) NULL COMMENT '会话标题',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/CLOSED',
  context_json JSON NULL COMMENT '上下文摘要',
  last_active_at DATETIME NULL COMMENT '最后活跃时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_ai_session_key (session_key),
  KEY idx_ai_session_user (user_id, status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能体会话';

CREATE TABLE IF NOT EXISTS ai_agent_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  session_key VARCHAR(128) NOT NULL COMMENT '会话唯一键',
  role_type VARCHAR(32) NOT NULL COMMENT '角色 USER/ASSISTANT/TOOL/SYSTEM',
  content MEDIUMTEXT NOT NULL COMMENT '消息内容',
  tool_code VARCHAR(64) NULL COMMENT '工具编码',
  token_count INT NULL COMMENT 'Token 数量',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  KEY idx_ai_message_session (session_key, created_at, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI智能体消息';

CREATE TABLE IF NOT EXISTS ai_query_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  request_id VARCHAR(128) NOT NULL COMMENT '请求ID',
  user_id BIGINT NULL COMMENT '用户ID',
  question_text VARCHAR(2000) NOT NULL COMMENT '用户问题',
  matched_qa_id BIGINT NULL COMMENT '命中的标准答案ID',
  route_type VARCHAR(32) NOT NULL DEFAULT 'MODEL' COMMENT '路由类型 CACHE/QA/VECTOR/MODEL/TOOL',
  token_input_count INT NOT NULL DEFAULT 0 COMMENT '输入 Token 数',
  token_output_count INT NOT NULL DEFAULT 0 COMMENT '输出 Token 数',
  latency_ms BIGINT NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  cache_hit TINYINT NOT NULL DEFAULT 0 COMMENT '是否命中缓存',
  response_status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' COMMENT '响应状态',
  error_message VARCHAR(1000) NULL COMMENT '错误信息',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  UNIQUE KEY uk_ai_query_request (request_id),
  KEY idx_ai_query_user_time (user_id, created_at),
  KEY idx_ai_query_route (route_type, cache_hit, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI问答调用日志';

CREATE TABLE IF NOT EXISTS ai_feedback (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  query_log_id BIGINT NULL COMMENT '问答日志ID',
  user_id BIGINT NULL COMMENT '反馈用户ID',
  feedback_type VARCHAR(32) NOT NULL COMMENT '反馈类型 LIKE/DISLIKE/CORRECTION',
  rating INT NULL COMMENT '评分',
  comment VARCHAR(1000) NULL COMMENT '反馈内容',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  KEY idx_ai_feedback_query (query_log_id, is_deleted),
  KEY idx_ai_feedback_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI问答反馈';

CREATE TABLE IF NOT EXISTS ai_vector_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  doc_key VARCHAR(128) NOT NULL COMMENT '向量文档唯一键',
  collection_name VARCHAR(128) NOT NULL COMMENT 'Qdrant 集合名称',
  source_type VARCHAR(64) NOT NULL COMMENT '来源类型',
  source_id VARCHAR(128) NULL COMMENT '来源ID',
  vector_id VARCHAR(128) NULL COMMENT '向量ID',
  payload_json JSON NULL COMMENT '向量载荷',
  checksum VARCHAR(128) NULL COMMENT '内容校验码',
  sync_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '同步状态 PENDING/SYNCED/FAILED',
  last_synced_at DATETIME NULL COMMENT '最近同步时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_ai_vector_doc_key (doc_key),
  KEY idx_ai_vector_collection (collection_name, sync_status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI向量文档索引';

CREATE TABLE IF NOT EXISTS ai_vector_sync_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  job_no VARCHAR(64) NOT NULL COMMENT '任务编号',
  job_type VARCHAR(32) NOT NULL COMMENT '任务类型 FULL/INCREMENTAL/RETRY',
  collection_name VARCHAR(128) NOT NULL COMMENT 'Qdrant 集合名称',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING/RUNNING/SUCCESS/FAILED',
  total_count INT NOT NULL DEFAULT 0 COMMENT '总数',
  success_count INT NOT NULL DEFAULT 0 COMMENT '成功数',
  failed_count INT NOT NULL DEFAULT 0 COMMENT '失败数',
  started_at DATETIME NULL COMMENT '开始时间',
  finished_at DATETIME NULL COMMENT '结束时间',
  error_message VARCHAR(1000) NULL COMMENT '错误信息',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_ai_vector_job_no (job_no),
  KEY idx_ai_vector_job_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI向量同步任务';

CREATE TABLE IF NOT EXISTS ai_answer_cache_meta (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  cache_key VARCHAR(128) NOT NULL COMMENT '缓存键',
  cache_backend VARCHAR(32) NOT NULL DEFAULT 'REDIS' COMMENT '缓存后端',
  question_hash VARCHAR(128) NULL COMMENT '问题哈希',
  qa_id BIGINT NULL COMMENT '标准答案ID',
  ttl_seconds INT NOT NULL DEFAULT 86400 COMMENT '缓存秒数',
  expires_at DATETIME NULL COMMENT '过期时间',
  hit_count BIGINT NOT NULL DEFAULT 0 COMMENT '命中次数',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态 ACTIVE/EXPIRED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
  version INT NOT NULL DEFAULT 0 COMMENT '版本号',
  UNIQUE KEY uk_ai_cache_key (cache_key),
  KEY idx_ai_cache_hash (question_hash, status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI答案缓存元数据';

INSERT INTO bi_dataset
(dataset_code, dataset_name, source_table, biz_domain, visibility_scope, refresh_mode, description, enabled, created_by, updated_by, is_deleted, version)
VALUES
('purchase_request', '采购申请', 'purchase_request', 'PROCUREMENT', 'INTERNAL', 'REALTIME', '采购申请主表，承载申请单号、标题、状态、金额、部门和流程时间', 1, 0, 0, 0, 0),
('purchase_request_item', '采购明细', 'purchase_request_item', 'PROCUREMENT', 'INTERNAL', 'REALTIME', '采购申请明细，承载商品、数量、金额、收货城市和送达地址', 1, 0, 0, 0, 0),
('supplier', '供应商', 'supplier', 'PROCUREMENT', 'INTERNAL', 'REALTIME', '供应商主数据，承载供应商状态、封禁和暂停投标信息', 1, 0, 0, 0, 0),
('supplier_quote', '供应商报价', 'supplier_quote', 'PROCUREMENT', 'INTERNAL', 'REALTIME', '供应商报价主表，承载报价金额、交期和中标确认状态', 1, 0, 0, 0, 0),
('supplier_quote_item', '报价明细', 'supplier_quote_item', 'PROCUREMENT', 'INTERNAL', 'REALTIME', '供应商报价明细，承载单项报价、中标、收货和售后衔接信息', 1, 0, 0, 0, 0),
('procurement_after_sale', '售后申请', 'procurement_after_sale', 'PROCUREMENT', 'INTERNAL', 'REALTIME', '采购订单售后申请，承载退货、换货、维修和供应商处理状态', 1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  dataset_name = VALUES(dataset_name),
  source_table = VALUES(source_table),
  biz_domain = VALUES(biz_domain),
  visibility_scope = VALUES(visibility_scope),
  refresh_mode = VALUES(refresh_mode),
  description = VALUES(description),
  enabled = VALUES(enabled),
  updated_by = VALUES(updated_by);

INSERT INTO bi_dataset_field
(dataset_id, field_code, field_name, physical_column, data_type, semantic_type, unit, default_aggregation, filterable, groupable, sortable, sensitive_flag, description, sort_no, created_by, updated_by, is_deleted, version)
VALUES
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request'), 'req_no', '申请单号', 'req_no', 'STRING', 'BUSINESS_KEY', NULL, NULL, 1, 1, 1, 0, '采购申请单号', 10, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request'), 'req_title', '申请标题', 'req_title', 'STRING', 'TITLE', NULL, NULL, 1, 1, 1, 0, '采购申请标题', 20, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request'), 'req_type', '申请类型', 'req_type', 'STRING', 'ENUM', NULL, NULL, 1, 1, 1, 0, '普通采购或紧急采购', 30, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request'), 'dept_name', '部门/机房', 'dept_name', 'STRING', 'ORG', NULL, NULL, 1, 1, 1, 0, '申请所属部门或机房楼栋', 40, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request'), 'total_amount', '申请金额', 'total_amount', 'NUMBER', 'MONEY', '元', 'SUM', 1, 1, 1, 0, '采购申请总金额', 50, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request'), 'approval_status', '审批状态', 'approval_status', 'STRING', 'ENUM', NULL, NULL, 1, 1, 1, 0, '审批状态', 60, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request'), 'status', '业务状态', 'status', 'STRING', 'ENUM', NULL, NULL, 1, 1, 1, 0, '采购业务状态', 70, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request'), 'submitted_at', '提交时间', 'submitted_at', 'DATETIME', 'TIME', NULL, NULL, 1, 1, 1, 0, '提交审批时间', 80, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request'), 'approved_at', '通过时间', 'approved_at', 'DATETIME', 'TIME', NULL, NULL, 1, 1, 1, 0, '审批通过时间', 90, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request_item'), 'item_name', '商品名称', 'item_name', 'STRING', 'PRODUCT', NULL, NULL, 1, 1, 1, 0, '采购商品名称', 10, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request_item'), 'spec_model', '规格型号', 'spec_model', 'STRING', 'PRODUCT_SPEC', NULL, NULL, 1, 1, 1, 0, '采购商品规格型号', 20, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request_item'), 'qty', '采购数量', 'qty', 'NUMBER', 'QUANTITY', NULL, 'SUM', 1, 1, 1, 0, '采购申请数量', 30, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request_item'), 'unit', '单位', 'unit', 'STRING', 'UNIT', NULL, NULL, 1, 1, 1, 0, '计量单位', 40, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request_item'), 'line_amount', '明细金额', 'line_amount', 'NUMBER', 'MONEY', '元', 'SUM', 1, 1, 1, 0, '采购明细金额', 50, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request_item'), 'receiving_city', '收货城市', 'receiving_city', 'STRING', 'CITY', NULL, NULL, 1, 1, 1, 0, '采购收货城市', 60, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request_item'), 'delivery_address', '送达地址', 'delivery_address', 'STRING', 'ADDRESS', NULL, NULL, 1, 1, 1, 0, '采购送达地址', 70, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'purchase_request_item'), 'status', '明细状态', 'status', 'STRING', 'ENUM', NULL, NULL, 1, 1, 1, 0, '采购明细状态', 80, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier'), 'supplier_code', '供应商编码', 'supplier_code', 'STRING', 'BUSINESS_KEY', NULL, NULL, 1, 1, 1, 0, '供应商编码', 10, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier'), 'supplier_name', '供应商名称', 'supplier_name', 'STRING', 'PARTNER', NULL, NULL, 1, 1, 1, 0, '供应商名称', 20, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier'), 'status', '供应商状态', 'status', 'STRING', 'ENUM', NULL, NULL, 1, 1, 1, 0, '供应商启停状态', 30, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier'), 'blacklist_flag', '封禁标记', 'blacklist_flag', 'BOOLEAN', 'FLAG', NULL, NULL, 1, 1, 1, 0, '是否封禁供应商', 40, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier'), 'bid_suspended_until', '暂停投标截止时间', 'bid_suspended_until', 'DATETIME', 'TIME', NULL, NULL, 1, 1, 1, 0, '供应商暂停投标截止时间', 50, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote'), 'quote_no', '报价单号', 'quote_no', 'STRING', 'BUSINESS_KEY', NULL, NULL, 1, 1, 1, 0, '供应商报价单号', 10, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote'), 'total_amount', '报价金额', 'total_amount', 'NUMBER', 'MONEY', '元', 'SUM', 1, 1, 1, 0, '报价总金额', 20, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote'), 'lead_time_days', '交期天数', 'lead_time_days', 'NUMBER', 'DAYS', '天', 'AVG', 1, 1, 1, 0, '报价交期天数', 30, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote'), 'status', '报价状态', 'status', 'STRING', 'ENUM', NULL, NULL, 1, 1, 1, 0, '供应商报价状态', 40, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote'), 'supplier_confirm_status', '中标确认状态', 'supplier_confirm_status', 'STRING', 'ENUM', NULL, NULL, 1, 1, 1, 0, '供应商中标确认状态', 50, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote'), 'submitted_at', '报价提交时间', 'submitted_at', 'DATETIME', 'TIME', NULL, NULL, 1, 1, 1, 0, '报价提交时间', 60, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote_item'), 'item_name', '报价商品名称', 'item_name', 'STRING', 'PRODUCT', NULL, NULL, 1, 1, 1, 0, '报价商品名称', 10, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote_item'), 'unit_price', '报价单价', 'unit_price', 'NUMBER', 'MONEY', '元', 'AVG', 1, 1, 1, 0, '报价单价', 20, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote_item'), 'line_amount', '报价金额', 'line_amount', 'NUMBER', 'MONEY', '元', 'SUM', 1, 1, 1, 0, '报价明细金额', 30, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote_item'), 'lead_time_days', '明细交期', 'lead_time_days', 'NUMBER', 'DAYS', '天', 'AVG', 1, 1, 1, 0, '报价明细交期', 40, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote_item'), 'awarded', '是否中标', 'awarded', 'BOOLEAN', 'FLAG', NULL, NULL, 1, 1, 1, 0, '报价明细是否中标', 50, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'supplier_quote_item'), 'receipt_status', '收货状态', 'receipt_status', 'STRING', 'ENUM', NULL, NULL, 1, 1, 1, 0, '中标明细收货状态', 60, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'procurement_after_sale'), 'after_sale_no', '售后单号', 'after_sale_no', 'STRING', 'BUSINESS_KEY', NULL, NULL, 1, 1, 1, 0, '售后申请单号', 10, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'procurement_after_sale'), 'after_sale_type', '售后类型', 'after_sale_type', 'STRING', 'ENUM', NULL, NULL, 1, 1, 1, 0, '售后类型', 20, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'procurement_after_sale'), 'status', '售后状态', 'status', 'STRING', 'ENUM', NULL, NULL, 1, 1, 1, 0, '售后处理状态', 30, 0, 0, 0, 0),
((SELECT id FROM bi_dataset WHERE dataset_code = 'procurement_after_sale'), 'requested_qty', '售后数量', 'requested_qty', 'NUMBER', 'QUANTITY', NULL, 'SUM', 1, 1, 1, 0, '售后申请数量', 40, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  field_name = VALUES(field_name),
  physical_column = VALUES(physical_column),
  data_type = VALUES(data_type),
  semantic_type = VALUES(semantic_type),
  unit = VALUES(unit),
  default_aggregation = VALUES(default_aggregation),
  filterable = VALUES(filterable),
  groupable = VALUES(groupable),
  sortable = VALUES(sortable),
  sensitive_flag = VALUES(sensitive_flag),
  description = VALUES(description),
  sort_no = VALUES(sort_no),
  updated_by = VALUES(updated_by);

INSERT INTO bi_dataset_relation
(relation_code, source_dataset_code, target_dataset_code, relation_type, join_condition, description, enabled, created_by, updated_by, is_deleted, version)
VALUES
('purchase_request__purchase_request_item', 'purchase_request', 'purchase_request_item', 'LEFT_JOIN', 'purchase_request.id = purchase_request_item.purchase_request_id', '采购申请关联采购明细', 1, 0, 0, 0, 0),
('purchase_request__supplier_quote', 'purchase_request', 'supplier_quote', 'LEFT_JOIN', 'purchase_request.id = supplier_quote.purchase_request_id', '采购申请关联供应商报价', 1, 0, 0, 0, 0),
('supplier__supplier_quote', 'supplier', 'supplier_quote', 'LEFT_JOIN', 'supplier.id = supplier_quote.supplier_id', '供应商关联报价单', 1, 0, 0, 0, 0),
('supplier_quote__supplier_quote_item', 'supplier_quote', 'supplier_quote_item', 'LEFT_JOIN', 'supplier_quote.id = supplier_quote_item.quote_id', '报价单关联报价明细', 1, 0, 0, 0, 0),
('purchase_request_item__supplier_quote_item', 'purchase_request_item', 'supplier_quote_item', 'LEFT_JOIN', 'purchase_request_item.id = supplier_quote_item.purchase_request_item_id', '采购明细关联报价明细', 1, 0, 0, 0, 0),
('supplier_quote_item__procurement_after_sale', 'supplier_quote_item', 'procurement_after_sale', 'LEFT_JOIN', 'supplier_quote_item.id = procurement_after_sale.quote_item_id', '报价明细关联售后申请', 1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  source_dataset_code = VALUES(source_dataset_code),
  target_dataset_code = VALUES(target_dataset_code),
  relation_type = VALUES(relation_type),
  join_condition = VALUES(join_condition),
  description = VALUES(description),
  enabled = VALUES(enabled),
  updated_by = VALUES(updated_by);

INSERT INTO bi_metric
(metric_code, metric_name, dataset_code, metric_type, expression_sql, unit, dimension_json, description, enabled, created_by, updated_by, is_deleted, version)
VALUES
('awarded_amount', '中标金额', 'supplier_quote_item', 'MEASURE', 'SUM(CASE WHEN awarded = 1 AND is_deleted = 0 THEN line_amount ELSE 0 END)', '元', JSON_OBJECT('defaultDimensions', JSON_ARRAY('supplier_name', 'dept_name', 'receiving_city')), '已中标报价明细金额合计', 1, 0, 0, 0, 0),
('procurement_quantity', '采购数量', 'purchase_request_item', 'MEASURE', 'SUM(CASE WHEN is_deleted = 0 THEN qty ELSE 0 END)', NULL, JSON_OBJECT('defaultDimensions', JSON_ARRAY('item_name', 'dept_name')), '采购申请明细数量合计', 1, 0, 0, 0, 0),
('awarded_supplier_count', '成交供应商', 'supplier_quote', 'COUNT', 'COUNT(DISTINCT CASE WHEN supplier_confirm_status IN (''PENDING'', ''CONFIRMED'') AND is_deleted = 0 THEN supplier_id END)', '家', JSON_OBJECT('defaultDimensions', JSON_ARRAY('status')), '有中标记录的供应商数量', 1, 0, 0, 0, 0),
('average_win_rate', '平均中单率', 'supplier_quote', 'RATIO', 'COUNT(DISTINCT CASE WHEN supplier_confirm_status IN (''PENDING'', ''CONFIRMED'') THEN id END) / NULLIF(COUNT(DISTINCT id), 0)', '%', JSON_OBJECT('defaultDimensions', JSON_ARRAY('supplier_name')), '供应商报价中单率', 1, 0, 0, 0, 0),
('average_lead_time', '平均交期', 'supplier_quote_item', 'MEASURE', 'AVG(CASE WHEN awarded = 1 AND is_deleted = 0 THEN lead_time_days END)', '天', JSON_OBJECT('defaultDimensions', JSON_ARRAY('supplier_name', 'item_name')), '中标明细平均交期', 1, 0, 0, 0, 0),
('after_sale_count', '售后申请数', 'procurement_after_sale', 'COUNT', 'COUNT(CASE WHEN is_deleted = 0 THEN id END)', '单', JSON_OBJECT('defaultDimensions', JSON_ARRAY('after_sale_type', 'status')), '售后申请数量', 1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  metric_name = VALUES(metric_name),
  dataset_code = VALUES(dataset_code),
  metric_type = VALUES(metric_type),
  expression_sql = VALUES(expression_sql),
  unit = VALUES(unit),
  dimension_json = VALUES(dimension_json),
  description = VALUES(description),
  enabled = VALUES(enabled),
  updated_by = VALUES(updated_by);

INSERT INTO bi_dashboard
(dashboard_code, dashboard_name, route_path, owner_user_id, layout_schema, description, status, created_by, updated_by, is_deleted, version)
VALUES
('procurement_board', '采购看板', '/procurement/dashboard', 0, JSON_OBJECT('columns', 20, 'mode', 'canvas'), '采购经营看板默认定义，后续可承载用户自定义组件和智能体生成视图', 'ACTIVE', 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  dashboard_name = VALUES(dashboard_name),
  route_path = VALUES(route_path),
  layout_schema = VALUES(layout_schema),
  description = VALUES(description),
  status = VALUES(status),
  updated_by = VALUES(updated_by);

INSERT INTO bi_dashboard_widget
(dashboard_code, widget_code, widget_name, widget_type, dataset_code, metric_codes, query_config, layout_config, sort_no, enabled, created_by, updated_by, is_deleted, version)
VALUES
('procurement_board', 'metric_awarded_amount', '中标金额', 'METRIC', 'supplier_quote_item', JSON_ARRAY('awarded_amount'), JSON_OBJECT(), JSON_OBJECT('size', 'small', 'height', 'short'), 10, 1, 0, 0, 0, 0),
('procurement_board', 'metric_procurement_quantity', '采购数量', 'METRIC', 'purchase_request_item', JSON_ARRAY('procurement_quantity'), JSON_OBJECT(), JSON_OBJECT('size', 'small', 'height', 'short'), 20, 1, 0, 0, 0, 0),
('procurement_board', 'metric_supplier_count', '成交供应商', 'METRIC', 'supplier_quote', JSON_ARRAY('awarded_supplier_count'), JSON_OBJECT(), JSON_OBJECT('size', 'small', 'height', 'short'), 30, 1, 0, 0, 0, 0),
('procurement_board', 'metric_win_rate', '平均中单率', 'METRIC', 'supplier_quote', JSON_ARRAY('average_win_rate'), JSON_OBJECT(), JSON_OBJECT('size', 'small', 'height', 'short'), 40, 1, 0, 0, 0, 0),
('procurement_board', 'table_procurement_detail', '采购明细', 'TABLE', 'purchase_request_item', JSON_ARRAY('awarded_amount', 'procurement_quantity'), JSON_OBJECT('defaultFields', JSON_ARRAY('item_name', 'spec_model', 'qty', 'unit', 'line_amount', 'receiving_city')), JSON_OBJECT('size', 'full', 'height', 'tall'), 100, 1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  widget_name = VALUES(widget_name),
  widget_type = VALUES(widget_type),
  dataset_code = VALUES(dataset_code),
  metric_codes = VALUES(metric_codes),
  query_config = VALUES(query_config),
  layout_config = VALUES(layout_config),
  sort_no = VALUES(sort_no),
  enabled = VALUES(enabled),
  updated_by = VALUES(updated_by);

INSERT INTO bi_dashboard_filter
(dashboard_code, filter_code, filter_name, dataset_code, field_code, filter_type, default_value, sort_no, enabled, created_by, updated_by, is_deleted, version)
VALUES
('procurement_board', 'submitted_at', '提交时间', 'purchase_request', 'submitted_at', 'DATE_RANGE', NULL, 10, 1, 0, 0, 0, 0),
('procurement_board', 'dept_name', '部门/机房', 'purchase_request', 'dept_name', 'SELECT', NULL, 20, 1, 0, 0, 0, 0),
('procurement_board', 'receiving_city', '收货城市', 'purchase_request_item', 'receiving_city', 'SELECT', NULL, 30, 1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  filter_name = VALUES(filter_name),
  dataset_code = VALUES(dataset_code),
  field_code = VALUES(field_code),
  filter_type = VALUES(filter_type),
  default_value = VALUES(default_value),
  sort_no = VALUES(sort_no),
  enabled = VALUES(enabled),
  updated_by = VALUES(updated_by);

INSERT INTO ai_prompt_template
(template_code, template_name, scenario_code, system_prompt, user_prompt_template, model_config, enabled, created_by, updated_by, is_deleted, version)
VALUES
('procurement_data_qa', '采购数据问答', 'PROCUREMENT_DATA_QA', '你是企业采购平台的数据助手。回答时优先使用已授权的采购数据集、缓存答案和知识库，不输出用户无权限查看的数据。', '用户问题：{{question}}\n可用数据集：{{datasets}}\n请返回简洁的中文答案，并给出使用的数据口径。', JSON_OBJECT('temperature', 0.2, 'maxTokens', 1200), 1, 0, 0, 0, 0),
('procurement_policy_qa', '采购规则问答', 'PROCUREMENT_POLICY_QA', '你是企业采购规则助手。回答时优先匹配常见问题库和知识片段。', '用户问题：{{question}}\n相关知识：{{context}}\n请用中文回答，并指出适用条件。', JSON_OBJECT('temperature', 0.1, 'maxTokens', 1000), 1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  template_name = VALUES(template_name),
  scenario_code = VALUES(scenario_code),
  system_prompt = VALUES(system_prompt),
  user_prompt_template = VALUES(user_prompt_template),
  model_config = VALUES(model_config),
  enabled = VALUES(enabled),
  updated_by = VALUES(updated_by);

INSERT INTO ai_agent_tool
(tool_code, tool_name, tool_type, endpoint, request_schema, response_schema, auth_type, enabled, created_by, updated_by, is_deleted, version)
VALUES
('procurement_dashboard_query', '采购看板数据查询', 'HTTP', '/api/workflow/dashboard', JSON_OBJECT('method', 'GET'), JSON_OBJECT('type', 'dashboard'), 'USER', 1, 0, 0, 0, 0),
('shopping_product_search', '电商商品搜索', 'HTTP', '/api/integrations/shopping/search', JSON_OBJECT('method', 'POST'), JSON_OBJECT('type', 'products'), 'SYSTEM', 1, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE
  tool_name = VALUES(tool_name),
  tool_type = VALUES(tool_type),
  endpoint = VALUES(endpoint),
  request_schema = VALUES(request_schema),
  response_schema = VALUES(response_schema),
  auth_type = VALUES(auth_type),
  enabled = VALUES(enabled),
  updated_by = VALUES(updated_by);
