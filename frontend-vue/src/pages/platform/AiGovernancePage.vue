<template>
  <VnetPage title="AI 知识与技能">
    <template #actions>
      <el-button :loading="loading" @click="loadOverview">刷新</el-button>
    </template>

    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="模型只能提交改进建议，管理员审核通过后才会生成新修订并生效。"
      description="知识库和 Skill 的每次新增、修改、删除及建议审批都会保留修订记录；运行中的采购合规审查会读取当前启用版本。"
    />

    <section class="overview-grid" aria-label="AI内容概览">
      <article>
        <span>知识库</span>
        <strong>{{ knowledgeBases.length }}</strong>
        <small>{{ enabledCount(knowledgeBases) }} 个启用</small>
      </article>
      <article>
        <span>Skill 库</span>
        <strong>{{ skills.length }}</strong>
        <small>{{ enabledCount(skills) }} 个启用</small>
      </article>
      <article :class="{ pending: pendingProposalCount > 0 }">
        <span>待审核改进</span>
        <strong>{{ pendingProposalCount }}</strong>
        <small>必须由管理员处理</small>
      </article>
    </section>

    <VnetPanel title="内容管理">
      <el-tabs v-model="activeTab">
        <el-tab-pane name="knowledge">
          <template #label>知识库</template>
          <div class="table-toolbar">
            <p>维护可被多个 Skill 引用的制度、规则和业务知识。</p>
            <el-button type="primary" @click="openKnowledgeEditor()">新建知识库</el-button>
          </div>
          <el-table v-loading="loading" :data="knowledgeBases" stripe>
            <el-table-column label="知识库" min-width="230">
              <template #default="{ row }">
                <div class="name-cell">
                  <strong>{{ row.knowledgeName }}</strong>
                  <code>{{ row.knowledgeCode }}</code>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="280" show-overflow-tooltip />
            <el-table-column label="修订" width="90">
              <template #default="{ row }">R{{ row.revisionNo }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? "启用" : "停用" }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="openKnowledgeEditor(row)">编辑</el-button>
                <el-button type="primary" link @click="openHistory('KNOWLEDGE_BASE', row.id, row.knowledgeName)">历史</el-button>
                <el-button type="danger" link @click="removeKnowledge(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane name="skills">
          <template #label>Skill 库</template>
          <div class="table-toolbar">
            <p>Skill 定义模型的执行步骤，并按需引用一个或多个知识库。</p>
            <el-button type="primary" @click="openSkillEditor()">新建 Skill</el-button>
          </div>
          <el-table v-loading="loading" :data="skills" stripe>
            <el-table-column label="Skill" min-width="230">
              <template #default="{ row }">
                <div class="name-cell">
                  <strong>{{ row.skillName }}</strong>
                  <code>{{ row.skillCode }}</code>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="引用知识库" min-width="280">
              <template #default="{ row }">
                <div class="tag-list">
                  <el-tag v-for="code in row.knowledgeBaseCodes" :key="code" effect="plain">{{ knowledgeName(code) }}</el-tag>
                  <span v-if="row.knowledgeBaseCodes.length === 0">未引用</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="修订" width="90">
              <template #default="{ row }">R{{ row.revisionNo }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? "启用" : "停用" }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="openSkillEditor(row)">编辑</el-button>
                <el-button type="primary" link @click="openHistory('SKILL', row.id, row.skillName)">历史</el-button>
                <el-button type="danger" link @click="removeSkill(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane name="proposals">
          <template #label>
            改进审核
            <el-badge v-if="pendingProposalCount > 0" :value="pendingProposalCount" class="tab-badge" />
          </template>
          <div class="table-toolbar">
            <p>模型建议仅供比较；审核通过会完整替换目标正文并生成新修订。</p>
            <el-select v-model="proposalStatus" style="width: 150px">
              <el-option label="待审核" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已拒绝" value="REJECTED" />
              <el-option label="全部" value="ALL" />
            </el-select>
          </div>
          <el-table v-loading="loading" :data="visibleProposals" stripe>
            <el-table-column label="目标" min-width="220">
              <template #default="{ row }">
                <div class="name-cell">
                  <strong>{{ row.targetType === "SKILL" ? "Skill" : "知识库" }}</strong>
                  <code>{{ row.targetCode }}</code>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="rationale" label="模型改进理由" min-width="360" show-overflow-tooltip />
            <el-table-column label="来源" min-width="180">
              <template #default="{ row }">{{ sourceText(row) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="proposalTagType(row.status)">{{ proposalStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="提出时间" min-width="170" />
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="openProposal(row)">对比</el-button>
                <template v-if="row.status === 'PENDING'">
                  <el-button type="success" link @click="reviewProposal(row, 'APPROVE')">通过</el-button>
                  <el-button type="danger" link @click="reviewProposal(row, 'REJECT')">拒绝</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!loading && visibleProposals.length === 0" description="当前没有对应的改进建议" />
        </el-tab-pane>
      </el-tabs>
    </VnetPanel>

    <el-dialog v-model="knowledgeDialogVisible" :title="editingKnowledgeId ? '编辑知识库' : '新建知识库'" width="820px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="知识库名称" required>
            <el-input v-model="knowledgeForm.knowledgeName" maxlength="128" />
          </el-form-item>
          <el-form-item label="知识库编码" required>
            <el-input v-model="knowledgeForm.knowledgeCode" :disabled="Boolean(editingKnowledgeId)" placeholder="lowercase-code" />
          </el-form-item>
        </div>
        <el-form-item label="说明">
          <el-input v-model="knowledgeForm.description" maxlength="500" />
        </el-form-item>
        <el-form-item label="知识正文（支持 Markdown）" required>
          <el-input v-model="knowledgeForm.content" type="textarea" :rows="16" maxlength="30000" show-word-limit />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="状态">
            <el-switch v-model="knowledgeEnabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="变更说明">
            <el-input v-model="knowledgeForm.changeSummary" maxlength="500" placeholder="说明本次修改目的" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="knowledgeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveKnowledge">保存并生成修订</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="skillDialogVisible" :title="editingSkillId ? '编辑 Skill' : '新建 Skill'" width="880px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="Skill 名称" required>
            <el-input v-model="skillForm.skillName" maxlength="128" />
          </el-form-item>
          <el-form-item label="Skill 编码" required>
            <el-input v-model="skillForm.skillCode" :disabled="Boolean(editingSkillId)" placeholder="lowercase-code" />
          </el-form-item>
        </div>
        <el-form-item label="说明">
          <el-input v-model="skillForm.description" maxlength="500" />
        </el-form-item>
        <el-form-item label="引用知识库">
          <el-select v-model="skillForm.knowledgeBaseCodes" multiple clearable style="width: 100%" placeholder="选择运行时引用的知识库">
            <el-option
              v-for="item in knowledgeBases"
              :key="item.knowledgeCode"
              :label="`${item.knowledgeName} · ${item.knowledgeCode}`"
              :value="item.knowledgeCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="执行指令（支持 Markdown）" required>
          <el-input v-model="skillForm.instructions" type="textarea" :rows="17" maxlength="30000" show-word-limit />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="状态">
            <el-switch v-model="skillEnabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
          <el-form-item label="变更说明">
            <el-input v-model="skillForm.changeSummary" maxlength="500" placeholder="说明本次修改目的" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="skillDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSkill">保存并生成修订</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="historyDialogVisible" :title="`${historyTitle} · 修订历史`" width="900px">
      <el-table v-loading="historyLoading" :data="revisions" stripe>
        <el-table-column label="修订" width="80">
          <template #default="{ row }">R{{ row.revisionNo }}</template>
        </el-table-column>
        <el-table-column label="来源" width="130">
          <template #default="{ row }">{{ revisionSourceText(row.changeSource) }}</template>
        </el-table-column>
        <el-table-column prop="changeSummary" label="变更说明" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="操作人ID" width="100" />
        <el-table-column prop="createdAt" label="时间" min-width="170" />
        <el-table-column type="expand">
          <template #default="{ row }"><pre class="snapshot">{{ prettySnapshot(row.snapshotJson) }}</pre></template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="proposalDialogVisible" title="改进建议对比" width="1100px">
      <template v-if="currentProposal">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="目标">{{ currentProposal.targetCode }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ proposalStatusText(currentProposal.status) }} · 基于 R{{ currentProposal.baseRevisionNo }}</el-descriptions-item>
          <el-descriptions-item label="改进理由" :span="2">{{ currentProposal.rationale }}</el-descriptions-item>
          <el-descriptions-item v-if="currentProposal.reviewComment" label="审核意见" :span="2">{{ currentProposal.reviewComment }}</el-descriptions-item>
        </el-descriptions>
        <div class="compare-grid">
          <section>
            <h3>当前生效正文</h3>
            <pre>{{ currentProposal.currentContent || "目标已被删除" }}</pre>
          </section>
          <section>
            <h3>模型建议全文</h3>
            <pre>{{ currentProposal.proposedContent }}</pre>
          </section>
        </div>
      </template>
      <template #footer>
        <el-button @click="proposalDialogVisible = false">关闭</el-button>
        <template v-if="currentProposal?.status === 'PENDING'">
          <el-button type="danger" @click="reviewProposal(currentProposal, 'REJECT')">拒绝</el-button>
          <el-button type="success" @click="reviewProposal(currentProposal, 'APPROVE')">审核通过并生效</el-button>
        </template>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  createAiKnowledgeBaseApi,
  createAiSkillApi,
  deleteAiKnowledgeBaseApi,
  deleteAiSkillApi,
  getAiContentRevisionsApi,
  getAiGovernanceOverviewApi,
  reviewAiChangeProposalApi,
  updateAiKnowledgeBaseApi,
  updateAiSkillApi
} from "../../api/platform";
import type {
  AiChangeProposalVO,
  AiContentRevisionVO,
  AiKnowledgeBaseForm,
  AiKnowledgeBaseVO,
  AiSkillForm,
  AiSkillVO
} from "../../types/platform";

type ProposalAction = "APPROVE" | "REJECT";

const loading = ref(false);
const saving = ref(false);
const activeTab = ref("knowledge");
const proposalStatus = ref("PENDING");
const knowledgeBases = ref<AiKnowledgeBaseVO[]>([]);
const skills = ref<AiSkillVO[]>([]);
const proposals = ref<AiChangeProposalVO[]>([]);
const pendingProposalCount = ref(0);

const knowledgeDialogVisible = ref(false);
const skillDialogVisible = ref(false);
const historyDialogVisible = ref(false);
const proposalDialogVisible = ref(false);
const historyLoading = ref(false);
const editingKnowledgeId = ref<number | null>(null);
const editingSkillId = ref<number | null>(null);
const historyTitle = ref("");
const revisions = ref<AiContentRevisionVO[]>([]);
const currentProposal = ref<AiChangeProposalVO | null>(null);

const knowledgeForm = reactive<AiKnowledgeBaseForm>(emptyKnowledgeForm());
const skillForm = reactive<AiSkillForm>(emptySkillForm());
const knowledgeEnabled = computed({
  get: () => knowledgeForm.status === 1,
  set: (value: boolean) => { knowledgeForm.status = value ? 1 : 0; }
});
const skillEnabled = computed({
  get: () => skillForm.status === 1,
  set: (value: boolean) => { skillForm.status = value ? 1 : 0; }
});
const visibleProposals = computed(() => proposalStatus.value === "ALL"
  ? proposals.value
  : proposals.value.filter((item) => item.status === proposalStatus.value));

onMounted(() => { void loadOverview(); });

async function loadOverview(): Promise<void> {
  loading.value = true;
  try {
    const data = await getAiGovernanceOverviewApi();
    knowledgeBases.value = data.knowledgeBases;
    skills.value = data.skills;
    proposals.value = data.proposals;
    pendingProposalCount.value = data.pendingProposalCount;
  } finally {
    loading.value = false;
  }
}

function openKnowledgeEditor(row?: AiKnowledgeBaseVO): void {
  editingKnowledgeId.value = row?.id ?? null;
  Object.assign(knowledgeForm, row ? {
    knowledgeCode: row.knowledgeCode,
    knowledgeName: row.knowledgeName,
    description: row.description || "",
    content: row.content,
    status: row.status,
    changeSummary: ""
  } : emptyKnowledgeForm());
  knowledgeDialogVisible.value = true;
}

function openSkillEditor(row?: AiSkillVO): void {
  editingSkillId.value = row?.id ?? null;
  Object.assign(skillForm, row ? {
    skillCode: row.skillCode,
    skillName: row.skillName,
    description: row.description || "",
    instructions: row.instructions,
    knowledgeBaseCodes: [...row.knowledgeBaseCodes],
    status: row.status,
    changeSummary: ""
  } : emptySkillForm());
  skillDialogVisible.value = true;
}

async function saveKnowledge(): Promise<void> {
  if (!validateCode(knowledgeForm.knowledgeCode) || !knowledgeForm.knowledgeName.trim() || !knowledgeForm.content.trim()) {
    ElMessage.warning("请填写合法编码、名称和知识正文");
    return;
  }
  saving.value = true;
  try {
    const payload = { ...knowledgeForm };
    if (editingKnowledgeId.value) await updateAiKnowledgeBaseApi(editingKnowledgeId.value, payload);
    else await createAiKnowledgeBaseApi(payload);
    ElMessage.success("知识库已保存并生成修订");
    knowledgeDialogVisible.value = false;
    await loadOverview();
  } finally { saving.value = false; }
}

async function saveSkill(): Promise<void> {
  if (!validateCode(skillForm.skillCode) || !skillForm.skillName.trim() || !skillForm.instructions.trim()) {
    ElMessage.warning("请填写合法编码、名称和执行指令");
    return;
  }
  saving.value = true;
  try {
    const payload = { ...skillForm, knowledgeBaseCodes: [...skillForm.knowledgeBaseCodes] };
    if (editingSkillId.value) await updateAiSkillApi(editingSkillId.value, payload);
    else await createAiSkillApi(payload);
    ElMessage.success("Skill 已保存并生成修订");
    skillDialogVisible.value = false;
    await loadOverview();
  } finally { saving.value = false; }
}

async function removeKnowledge(row: AiKnowledgeBaseVO): Promise<void> {
  if (!(await confirmDelete(row.knowledgeName, "知识库"))) return;
  await deleteAiKnowledgeBaseApi(row.id);
  ElMessage.success("知识库已删除，历史修订仍保留");
  await loadOverview();
}

async function removeSkill(row: AiSkillVO): Promise<void> {
  if (!(await confirmDelete(row.skillName, "Skill"))) return;
  await deleteAiSkillApi(row.id);
  ElMessage.success("Skill 已删除，历史修订仍保留");
  await loadOverview();
}

async function confirmDelete(name: string, type: string): Promise<boolean> {
  return ElMessageBox.confirm(`确认删除${type}“${name}”？历史修订会保留。`, "删除确认", {
    type: "warning",
    confirmButtonText: "删除",
    cancelButtonText: "取消"
  }).then(() => true).catch(() => false);
}

async function openHistory(targetType: string, targetId: number, title: string): Promise<void> {
  historyTitle.value = title;
  historyDialogVisible.value = true;
  historyLoading.value = true;
  try { revisions.value = await getAiContentRevisionsApi(targetType, targetId); }
  finally { historyLoading.value = false; }
}

function openProposal(row: AiChangeProposalVO): void {
  currentProposal.value = row;
  proposalDialogVisible.value = true;
}

async function reviewProposal(row: AiChangeProposalVO, action: ProposalAction): Promise<void> {
  const result = await ElMessageBox.prompt(
    action === "APPROVE" ? "通过后建议全文会立即成为新修订，请确认已完成内容对比。" : "拒绝后当前生效内容不会改变。",
    action === "APPROVE" ? "审核通过并生效" : "拒绝改进建议",
    {
      type: action === "APPROVE" ? "warning" : "info",
      inputType: "textarea",
      inputPlaceholder: "填写审核意见（可选）",
      confirmButtonText: action === "APPROVE" ? "通过并生效" : "确认拒绝",
      cancelButtonText: "取消"
    }
  ).catch(() => null);
  if (!result) return;
  await reviewAiChangeProposalApi(row.id, { action, comment: result.value || "" });
  ElMessage.success(action === "APPROVE" ? "建议已通过并生成新修订" : "建议已拒绝");
  proposalDialogVisible.value = false;
  currentProposal.value = null;
  await loadOverview();
}

function emptyKnowledgeForm(): AiKnowledgeBaseForm {
  return { knowledgeCode: "", knowledgeName: "", description: "", content: "", status: 1, changeSummary: "" };
}

function emptySkillForm(): AiSkillForm {
  return { skillCode: "", skillName: "", description: "", instructions: "", knowledgeBaseCodes: [], status: 1, changeSummary: "" };
}

function validateCode(value: string): boolean { return /^[a-z0-9][a-z0-9-]{1,63}$/.test(value.trim()); }
function enabledCount(items: Array<{ status: number }>): number { return items.filter((item) => item.status === 1).length; }
function knowledgeName(code: string): string { return knowledgeBases.value.find((item) => item.knowledgeCode === code)?.knowledgeName || code; }
function sourceText(row: AiChangeProposalVO): string { return row.sourceBizId ? `采购申请 #${row.sourceBizId}` : row.sourceBizType || "模型运行"; }
function proposalStatusText(status: string): string { return ({ PENDING: "待审核", APPROVED: "已通过", REJECTED: "已拒绝" } as Record<string, string>)[status] || status; }
function proposalTagType(status: string): "warning" | "success" | "danger" | "info" { return status === "PENDING" ? "warning" : status === "APPROVED" ? "success" : status === "REJECTED" ? "danger" : "info"; }
function revisionSourceText(source: string): string { return ({ INITIAL: "初始版本", MANUAL: "管理员修改", MODEL_APPROVED: "模型建议通过", DELETE: "删除" } as Record<string, string>)[source] || source; }
function prettySnapshot(value: string): string {
  try { return JSON.stringify(JSON.parse(value), null, 2); }
  catch { return value || "-"; }
}
</script>

<style scoped>
.overview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin: 18px 0; }
.overview-grid article { display: grid; gap: 4px; padding: 18px 20px; border: 1px solid #dbe5ef; border-radius: 12px; background: #fff; }
.overview-grid article.pending { border-color: #f4c76f; background: #fffbeb; }
.overview-grid span, .overview-grid small { color: #64748b; }
.overview-grid strong { color: #0f172a; font-size: 30px; line-height: 1.1; }
.table-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin: 0 0 16px; }
.table-toolbar p { margin: 0; color: #64748b; }
.name-cell { display: grid; gap: 5px; }
.name-cell code { width: fit-content; color: #475569; font-size: 12px; }
.tag-list { display: flex; flex-wrap: wrap; gap: 6px; }
.form-grid, .compare-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; }
.compare-grid { margin-top: 18px; }
.compare-grid section { min-width: 0; }
.compare-grid h3 { margin: 0 0 10px; font-size: 15px; }
.compare-grid pre, .snapshot { max-height: 480px; margin: 0; padding: 14px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; border: 1px solid #dbe5ef; border-radius: 8px; background: #f8fafc; color: #334155; font: 13px/1.6 ui-monospace, SFMono-Regular, Consolas, monospace; }
.tab-badge { margin-left: 8px; }
@media (max-width: 900px) { .overview-grid, .form-grid, .compare-grid { grid-template-columns: 1fr; } }
</style>
