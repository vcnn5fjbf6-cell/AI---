SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS ai_knowledge_base (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  knowledge_code VARCHAR(64) NOT NULL COMMENT '知识库编码',
  knowledge_name VARCHAR(128) NOT NULL COMMENT '知识库名称',
  description VARCHAR(500) NULL COMMENT '知识库说明',
  content MEDIUMTEXT NOT NULL COMMENT '知识库正文',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  revision_no INT NOT NULL DEFAULT 1 COMMENT '内容修订号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  UNIQUE KEY uk_ai_knowledge_code (knowledge_code),
  KEY idx_ai_knowledge_status (status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识库';

CREATE TABLE IF NOT EXISTS ai_skill_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  skill_code VARCHAR(64) NOT NULL COMMENT 'Skill编码',
  skill_name VARCHAR(128) NOT NULL COMMENT 'Skill名称',
  description VARCHAR(500) NULL COMMENT 'Skill说明',
  instructions MEDIUMTEXT NOT NULL COMMENT 'Skill执行指令',
  knowledge_base_codes VARCHAR(1000) NULL COMMENT '引用知识库编码，逗号分隔',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
  revision_no INT NOT NULL DEFAULT 1 COMMENT '内容修订号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  UNIQUE KEY uk_ai_skill_code (skill_code),
  KEY idx_ai_skill_status (status, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Skill库';

CREATE TABLE IF NOT EXISTS ai_content_revision (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  target_type VARCHAR(32) NOT NULL COMMENT '目标类型：KNOWLEDGE_BASE/SKILL',
  target_id BIGINT NOT NULL COMMENT '目标ID',
  target_code VARCHAR(64) NOT NULL COMMENT '目标编码',
  revision_no INT NOT NULL COMMENT '修订号',
  snapshot_json MEDIUMTEXT NOT NULL COMMENT '该修订完整快照',
  change_source VARCHAR(32) NOT NULL COMMENT '来源：INITIAL/MANUAL/MODEL_APPROVED/DELETE',
  change_summary VARCHAR(500) NULL COMMENT '变更说明',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  UNIQUE KEY uk_ai_content_revision (target_type, target_id, revision_no),
  KEY idx_ai_content_revision_code (target_type, target_code, revision_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI知识与Skill修订历史';

CREATE TABLE IF NOT EXISTS ai_change_proposal (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  target_type VARCHAR(32) NOT NULL COMMENT '目标类型：KNOWLEDGE_BASE/SKILL',
  target_id BIGINT NOT NULL COMMENT '目标ID',
  target_code VARCHAR(64) NOT NULL COMMENT '目标编码',
  base_revision_no INT NOT NULL COMMENT '建议生成时的目标修订号',
  proposal_hash VARCHAR(64) NOT NULL COMMENT '建议去重摘要',
  proposed_content MEDIUMTEXT NOT NULL COMMENT '建议替换后的完整正文',
  rationale VARCHAR(1000) NOT NULL COMMENT '改进理由',
  source_biz_type VARCHAR(64) NULL COMMENT '来源业务类型',
  source_biz_id BIGINT NULL COMMENT '来源业务ID',
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/REJECTED',
  reviewer_user_id BIGINT NULL COMMENT '审核人用户ID',
  reviewer_name VARCHAR(128) NULL COMMENT '审核人姓名',
  review_comment VARCHAR(1000) NULL COMMENT '审核意见',
  reviewed_at DATETIME NULL COMMENT '审核时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  created_by BIGINT NOT NULL DEFAULT 0 COMMENT '创建人',
  updated_by BIGINT NOT NULL DEFAULT 0 COMMENT '更新人',
  is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  UNIQUE KEY uk_ai_change_proposal_hash (proposal_hash),
  KEY idx_ai_change_proposal_status (status, created_at),
  KEY idx_ai_change_proposal_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI内容改进审核建议';

INSERT INTO ai_knowledge_base
  (knowledge_code, knowledge_name, description, content, status, revision_no, created_by, updated_by, is_deleted, version)
SELECT
  'procurement-compliance-rules',
  '采购合规基础规则',
  '采购理由、数量规格、紧急性和风险结论的基础知识。',
  '# 采购合规审查基础规则\n\n## 理由充分性\n- “使用”“备用”“办公使用”“工作需要”“领导要求”等词语本身不能证明采购必要性。\n- 有效理由应尽量包含使用部门或地点、当前问题、采购目的和数量依据。\n- 商品名称与用途描述明显不相关时，建议补充；不要自行猜测隐藏用途。\n\n## 数量与规格\n- 数量较多不等于不合规，应检查是否说明覆盖范围、替换数量、人员数量或备用比例。\n- 型号、尺寸、接口、性能等会影响适用性时，缺少关键规格应建议补充。\n- 不以申请阶段参考单价判定供应商报价是否合规；最终价格由供应商报价和原审批流程确认。\n\n## 紧急采购\n- 紧急采购应说明突发事件、业务影响、期望到货时间，以及不能等待普通采购周期的原因。\n- 仅写“急用”“应急使用”不足以证明紧急性。\n\n## 风险结论\n- 信息缺失或表达含糊优先建议补充，不直接建议退回。\n- 明确个人消费、与业务无关、理由与商品明显冲突或明确规避流程时，可以建议退回。\n- 没有正式制度依据时，只指出风险和需人工核实的事项，不声称违反具体制度。',
  1, 1, 0, 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM ai_knowledge_base WHERE knowledge_code = 'procurement-compliance-rules' AND is_deleted = 0
);

INSERT INTO ai_skill_definition
  (skill_code, skill_name, description, instructions, knowledge_base_codes, status, revision_no, created_by, updated_by, is_deleted, version)
SELECT
  'procurement-compliance-review',
  '采购合规审查',
  '统一审查采购理由、用途匹配、数量依据和紧急性，只向审批人提供建议。',
  '# 采购合规审查\n\n1. 接受系统已完成的字段、数量、价格和金额硬校验，不推翻确定性校验结果。\n2. 判断采购理由和每项用途是否说明具体场景、现状、必要性与预期效果。\n3. 判断商品、规格、数量、需求日期与用途是否一致。\n4. 紧急采购必须说明事件、影响范围和时限依据。\n5. 区分申请阶段参考价格与供应商最终报价。\n6. 信息不足时建议补充；只有明确非业务用途、明显矛盾或高风险事实时才建议退回。\n7. 只输出结构化结论，不执行审批、退回、终止或数据库操作。\n8. 如发现规则或指令存在具体、可复用的缺口，可提出完整替换正文作为改进建议；建议只进入管理员审核队列，不能自行生效。',
  'procurement-compliance-rules',
  1, 1, 0, 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM ai_skill_definition WHERE skill_code = 'procurement-compliance-review' AND is_deleted = 0
);

INSERT INTO ai_content_revision
  (target_type, target_id, target_code, revision_no, snapshot_json, change_source, change_summary,
   created_by, updated_by, is_deleted, version)
SELECT
  'KNOWLEDGE_BASE', kb.id, kb.knowledge_code, kb.revision_no,
  JSON_OBJECT(
    'knowledgeCode', kb.knowledge_code,
    'knowledgeName', kb.knowledge_name,
    'description', kb.description,
    'content', kb.content,
    'status', kb.status
  ),
  'INITIAL', '系统内置采购合规知识', 0, 0, 0, 0
FROM ai_knowledge_base kb
WHERE kb.knowledge_code = 'procurement-compliance-rules'
  AND kb.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM ai_content_revision revision
    WHERE revision.target_type = 'KNOWLEDGE_BASE'
      AND revision.target_id = kb.id
      AND revision.revision_no = kb.revision_no
      AND revision.is_deleted = 0
  );

INSERT INTO ai_content_revision
  (target_type, target_id, target_code, revision_no, snapshot_json, change_source, change_summary,
   created_by, updated_by, is_deleted, version)
SELECT
  'SKILL', skill.id, skill.skill_code, skill.revision_no,
  JSON_OBJECT(
    'skillCode', skill.skill_code,
    'skillName', skill.skill_name,
    'description', skill.description,
    'instructions', skill.instructions,
    'knowledgeBaseCodes', JSON_ARRAY('procurement-compliance-rules'),
    'status', skill.status
  ),
  'INITIAL', '系统内置采购合规Skill', 0, 0, 0, 0
FROM ai_skill_definition skill
WHERE skill.skill_code = 'procurement-compliance-review'
  AND skill.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM ai_content_revision revision
    WHERE revision.target_type = 'SKILL'
      AND revision.target_id = skill.id
      AND revision.revision_no = skill.revision_no
      AND revision.is_deleted = 0
  );

INSERT INTO sys_permission
  (perm_code, perm_name, module_code, perm_type, parent_id, path, http_method, sort_no, status, description, created_by, updated_by, is_deleted, version)
SELECT 'platform_ai_governance:manage', 'AI知识与技能管理', 'PLATFORM', 'BUTTON', NULL,
       '/api/admin/ai-governance/**', '*', 106, 1,
       '维护知识库和Skill，审核模型改进建议并生成可追溯修订', 0, 0, 0, 0
WHERE NOT EXISTS (
  SELECT 1 FROM sys_permission WHERE perm_code = 'platform_ai_governance:manage' AND is_deleted = 0
);

INSERT INTO sys_role_permission (role_id, permission_id, status, created_by, updated_by, is_deleted, version)
SELECT r.id, p.id, 1, 0, 0, 0, 0
FROM sys_role r
JOIN sys_permission p ON p.perm_code = 'platform_ai_governance:manage' AND p.is_deleted = 0
WHERE r.role_code IN ('ADMIN', 'SYS_ADMIN', 'SUPER_ADMIN')
  AND r.is_deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission existing_perm
    WHERE existing_perm.role_id = r.id
      AND existing_perm.permission_id = p.id
      AND existing_perm.is_deleted = 0
  );
