<template>
  <VnetPage title="作业许可">
    <template #actions><el-button :icon="Link" @click="openPublicPortal">外部申请入口</el-button><el-button type="primary" :icon="Plus" @click="openCreate">新建总作业票</el-button></template>

    <div class="metric-grid">
      <VnetMetricCard label="总作业票" :value="String(stats.total || 0)" />
      <VnetMetricCard label="审批中" :value="String(stats.approving || 0)" tone="warning" />
      <VnetMetricCard label="已批准/执行中" :value="String(stats.approved || 0)" tone="success" />
      <VnetMetricCard label="已关闭" :value="String(stats.closed || 0)" />
    </div>

    <VnetPanel title="作业许可台账">
      <el-tabs v-model="viewMode" @tab-change="search">
        <el-tab-pane label="全部总票" name="all" />
        <el-tab-pane label="待我审批" name="pending" />
        <el-tab-pane label="我的申请" name="mine" />
      </el-tabs>
      <el-form class="filter-bar" :inline="true" @submit.prevent="search">
        <el-form-item label="关键字"><el-input v-model="query.keyword" placeholder="票号 / 作业名称 / 地点 / 申请人" clearable @keyup.enter="search" /></el-form-item>
        <el-form-item label="作业状态"><el-select v-model="query.status" placeholder="全部状态" clearable><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button @click="resetFilters">重置</el-button></el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="page.records" stripe @row-dblclick="openDetail">
        <el-table-column prop="permitNo" label="作业票编号" min-width="175" fixed="left" />
        <el-table-column prop="permitType" label="票种" min-width="120"><template #default="{ row }">{{ permitTypeLabel(row.permitType) }}</template></el-table-column>
        <el-table-column prop="title" label="作业名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="location" label="作业地点" min-width="160" show-overflow-tooltip />
        <el-table-column prop="applicantName" label="申请人" width="110" />
        <el-table-column prop="plannedStartAt" label="计划开始" min-width="150"><template #default="{ row }">{{ formatTime(row.plannedStartAt) }}</template></el-table-column>
        <el-table-column prop="currentApproverName" label="当前审批人" width="120"><template #default="{ row }">{{ row.currentApproverName || "-" }}</template></el-table-column>
        <el-table-column prop="status" label="状态" width="130"><template #default="{ row }"><el-tag :type="statusType(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button><el-button v-if="row.canEdit" link type="primary" @click="openEdit(row)">编辑</el-button><el-button v-if="row.canSubmit" link type="success" @click="submitPermit(row)">提交</el-button><el-button v-if="row.status === 'DRAFT'" link type="danger" @click="removePermit(row)">删除</el-button></template></el-table-column>
        <template #empty><el-empty description="暂无作业许可记录" :image-size="72" /></template>
      </el-table>
      <div class="pagination-row"><el-pagination v-model:current-page="query.pageNo" v-model:page-size="query.pageSize" :total="page.total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
    </VnetPanel>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑作业票' : '新建总作业票'" width="900px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px" class="permit-form">
        <el-divider content-position="left">基本信息</el-divider>
        <el-row :gutter="16">
          <el-col :span="16"><el-form-item label="作业名称" prop="title"><el-input v-model="form.title" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="施工单位"><el-input v-model="form.contractorName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="作业区域"><el-input v-model="form.region" /></el-form-item></el-col>
          <el-col :span="16"><el-form-item label="作业地点" prop="location"><el-input v-model="form.location" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="作业内容" prop="workContent"><el-input v-model="form.workContent" type="textarea" :rows="3" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计划开始" prop="plannedStartAt"><el-date-picker v-model="form.plannedStartAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="计划结束" prop="plannedEndAt"><el-date-picker v-model="form.plannedEndAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">审批部门与专项票</el-divider>
        <el-form-item label="作业部门" prop="approvalRouteId"><el-select v-model="form.approvalRouteId" filterable placeholder="选择部门审批配置"><el-option v-for="item in departments" :key="item.approvalRouteId" :label="item.label" :value="item.approvalRouteId" /></el-select></el-form-item>
        <el-form-item v-if="editingPermitType === 'WORK_PERMIT'" label="专项作业票"><el-checkbox-group v-model="form.requiredPermitTypes"><el-checkbox v-for="type in specialPermitTypes" :key="type.value" :value="type.value">{{ type.label }}</el-checkbox></el-checkbox-group></el-form-item>

        <el-divider content-position="left">风险辨识与控制措施</el-divider>
        <div v-for="(item, index) in form.riskItems" :key="index" class="dynamic-row risk-row">
          <el-input v-model="item.risk" placeholder="风险" /><el-input v-model="item.control" placeholder="控制措施" /><el-button :icon="Delete" circle title="删除" @click="form.riskItems.splice(index, 1)" />
        </div>
        <el-button :icon="Plus" @click="form.riskItems.push({ risk: '', control: '' })">添加风险措施</el-button>

        <el-divider content-position="left">安全行为告知与关键步骤</el-divider>
        <el-form-item label="安全告知"><el-input v-model="form.safetyNotice.content" type="textarea" :rows="3" placeholder="填写施工人员必须遵守的安全行为、禁止事项和应急要求" /></el-form-item>
        <div v-for="(item, index) in form.operationSteps" :key="index" class="dynamic-row step-row">
          <el-input-number v-model="item.order" :min="1" controls-position="right" /><el-input v-model="item.risk" placeholder="操作步骤/风险" /><el-input v-model="item.control" placeholder="控制措施" /><el-input v-model="item.operator" placeholder="操作人" /><el-input v-model="item.confirmer" placeholder="确认人" /><el-button :icon="Delete" circle title="删除" @click="form.operationSteps.splice(index, 1)" />
        </div>
        <el-button :icon="Plus" @click="addOperationStep">添加关键步骤</el-button>

        <el-divider content-position="left">角色签名与附件</el-divider>
        <div v-for="(item, index) in form.signatures" :key="index" class="dynamic-row signature-row"><el-input v-model="item.role" placeholder="角色，如监护人" /><el-input v-model="item.name" placeholder="签名人" /><el-button :icon="Delete" circle title="删除" @click="form.signatures.splice(index, 1)" /></div>
        <el-button :icon="Plus" @click="form.signatures.push({ role: '', name: '' })">添加角色签名</el-button>
        <div v-for="(item, index) in form.attachments" :key="index" class="dynamic-row attachment-row"><el-input v-model="item.name" placeholder="附件名称" /><el-input v-model="item.url" placeholder="附件链接" /><el-button :icon="Delete" circle title="删除" @click="form.attachments.splice(index, 1)" /></div>
        <el-button :icon="Plus" @click="form.attachments.push({ name: '', url: '' })">添加附件</el-button>
        <el-form-item class="remark-field" label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="savePermit">保存草稿</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="detail?.permitNo || '作业票详情'" size="760px" destroy-on-close>
      <template v-if="detail">
        <div class="drawer-actions">
          <el-button v-if="detail.canEdit" :icon="Edit" @click="openEdit(detail)">编辑</el-button>
          <el-button v-if="detail.canSubmit" type="primary" :icon="Promotion" @click="submitPermit(detail)">提交审批</el-button>
          <el-button v-if="detail.canApprove" type="primary" :icon="Stamp" @click="openApproval(detail)">审批</el-button>
          <el-button v-if="detail.canStart" type="success" :icon="VideoPlay" @click="startPermit(detail)">确认开工</el-button>
          <el-button v-if="detail.permitType === 'CONFINED_SPACE' && ['APPROVED','IN_PROGRESS'].includes(detail.status)" :icon="DataAnalysis" @click="sampleVisible = true">气体采样</el-button>
          <el-button v-if="detail.canClose" type="warning" :icon="CircleCheck" @click="openClose(detail)">关单</el-button>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="票种">{{ permitTypeLabel(detail.permitType) }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="作业名称" :span="2">{{ detail.title }}</el-descriptions-item><el-descriptions-item label="申请人">{{ detail.applicantName }}</el-descriptions-item><el-descriptions-item label="施工单位">{{ detail.contractorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="作业地点" :span="2">{{ detail.region ? `${detail.region} / ` : '' }}{{ detail.location }}</el-descriptions-item><el-descriptions-item label="计划开始">{{ formatTime(detail.plannedStartAt) }}</el-descriptions-item><el-descriptions-item label="计划结束">{{ formatTime(detail.plannedEndAt) }}</el-descriptions-item>
          <el-descriptions-item label="作业部门" :span="2">{{ detail.deptName || '-' }}</el-descriptions-item><el-descriptions-item label="作业内容" :span="2">{{ detail.workContent }}</el-descriptions-item><el-descriptions-item label="当前审批节点">{{ detail.currentLevelName || '-' }}</el-descriptions-item><el-descriptions-item label="当前审批人">{{ detail.currentApproverName || '-' }}</el-descriptions-item><el-descriptions-item label="提交轮次">{{ detail.submitRound || 0 }}</el-descriptions-item>
        </el-descriptions>

        <section v-if="detail.children?.length" class="detail-section"><h3>专项作业票</h3><el-table :data="detail.children" size="small" border><el-table-column prop="permitNo" label="票号" min-width="170" /><el-table-column prop="permitType" label="类型" min-width="120"><template #default="{ row }">{{ permitTypeLabel(row.permitType) }}</template></el-table-column><el-table-column prop="status" label="状态" width="120"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column><el-table-column label="操作" width="90"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">打开</el-button></template></el-table-column></el-table></section>
        <section class="detail-section"><h3>风险辨识与控制措施</h3><el-table :data="detail.riskItems" size="small" border><el-table-column type="index" width="50" /><el-table-column prop="risk" label="风险" min-width="180" /><el-table-column prop="control" label="控制措施" min-width="240" /></el-table></section>
        <section class="detail-section"><h3>安全行为告知</h3><p class="notice-text">{{ detail.safetyNotice?.content || '未填写' }}</p></section>
        <section class="detail-section"><h3>关键设备操作步骤</h3><el-table :data="detail.operationSteps" size="small" border><el-table-column prop="order" label="序号" width="60" /><el-table-column prop="risk" label="步骤/风险" min-width="160" /><el-table-column prop="control" label="控制措施" min-width="180" /><el-table-column prop="operator" label="操作人" width="100" /><el-table-column prop="confirmer" label="确认人" width="100" /></el-table></section>
        <section v-if="detail.samples?.length" class="detail-section"><h3>受限空间气体采样</h3><el-table :data="detail.samples" size="small" border><el-table-column prop="sampledAt" label="采样时间" min-width="150" /><el-table-column prop="position" label="位置" width="100" /><el-table-column prop="oxygen" label="O₂(%)" width="80" /><el-table-column prop="lel" label="LEL(%)" width="85" /><el-table-column prop="carbonMonoxide" label="CO" width="70" /><el-table-column prop="hydrogenSulfide" label="H₂S" width="70" /><el-table-column prop="recorder" label="记录人" width="90" /></el-table></section>
        <section class="detail-section"><h3>审批记录</h3><el-timeline><el-timeline-item v-for="record in detail.approvalRecords" :key="record.id" :timestamp="formatTime(record.operatedAt)" placement="top"><strong>{{ record.nodeName }} · {{ actionLabel(record.action) }}</strong><p>{{ record.approverName || '系统' }}：{{ record.approvalComment || '-' }}</p></el-timeline-item></el-timeline><el-empty v-if="!detail.approvalRecords?.length" description="尚无审批记录" :image-size="52" /></section>
      </template>
    </el-drawer>

    <el-dialog v-model="approvalVisible" title="作业票审批" width="520px"><el-form label-width="90px"><el-form-item label="审批结果"><el-segmented v-model="approvalForm.action" :options="[{ label: '通过', value: 'APPROVE' }, { label: '退回', value: 'RETURN' }]" /></el-form-item><el-form-item label="审批意见"><el-input v-model="approvalForm.comment" type="textarea" :rows="4" /></el-form-item></el-form><template #footer><el-button @click="approvalVisible = false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="confirmApproval">确认</el-button></template></el-dialog>
    <el-dialog v-model="sampleVisible" title="受限空间气体采样" width="620px"><el-form label-width="100px"><el-form-item label="采样时间"><el-date-picker v-model="sampleForm.sampledAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="采样位置"><el-input v-model="sampleForm.position" /></el-form-item><div class="sample-grid"><el-form-item label="氧气(%)"><el-input-number v-model="sampleForm.oxygen" :precision="2" /></el-form-item><el-form-item label="LEL(%)"><el-input-number v-model="sampleForm.lel" :precision="2" /></el-form-item><el-form-item label="CO"><el-input-number v-model="sampleForm.carbonMonoxide" :precision="2" /></el-form-item><el-form-item label="H₂S"><el-input-number v-model="sampleForm.hydrogenSulfide" :precision="2" /></el-form-item></div><el-form-item label="记录人"><el-input v-model="sampleForm.recorder" /></el-form-item><el-form-item label="签名"><el-input v-model="sampleForm.signature" /></el-form-item></el-form><template #footer><el-button @click="sampleVisible = false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="saveSample">保存采样</el-button></template></el-dialog>
    <el-dialog v-model="closeVisible" title="作业票关单" width="560px"><el-form label-width="100px"><el-form-item label="关单结果"><el-select v-model="closeForm.outcome"><el-option label="正常完成" value="NORMAL" /><el-option label="取消作业" value="CANCELLED" /><el-option label="异常结束" value="ABNORMAL" /></el-select></el-form-item><el-form-item label="实际结束"><el-date-picker v-model="closeForm.actualEndAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="关单签名"><el-input v-model="closeForm.signature" /></el-form-item><el-form-item label="关单说明"><el-input v-model="closeForm.note" type="textarea" :rows="3" /></el-form-item><el-form-item label="照片链接"><el-input v-model="closeForm.photoText" type="textarea" :rows="3" placeholder="每行一个链接，最多 6 个" /></el-form-item></el-form><template #footer><el-button @click="closeVisible = false">取消</el-button><el-button type="primary" :loading="actionLoading" @click="confirmClose">确认关单</el-button></template></el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import { CircleCheck, DataAnalysis, Delete, Edit, Link, Plus, Promotion, Search, Stamp, VideoPlay } from "@element-plus/icons-vue";
import { addEhsPermitSampleApi, approveEhsPermitApi, closeEhsPermitApi, createEhsPermitApi, deleteEhsPermitApi, getEhsDepartmentsApi, getEhsPermitApi, getEhsPermitStatsApi, pageEhsPermitsApi, startEhsPermitApi, submitEhsPermitApi, updateEhsPermitApi } from "../../api/ehs";
import VnetMetricCard from "../../components/vnet/VnetMetricCard.vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import type { EhsDepartmentOption, EhsPermit, EhsPermitSavePayload, EhsPermitType } from "../../types/ehs";

const specialPermitTypes: Array<{ value: EhsPermitType; label: string }> = [
  { value: "HOT_WORK", label: "动火" }, { value: "HEIGHT_WORK", label: "高处" }, { value: "CONFINED_SPACE", label: "受限空间" }, { value: "ELECTRIC_WORK", label: "临时用电" }, { value: "LIFTING_WORK", label: "吊装" }, { value: "EXCAVATION_WORK", label: "动土" }
];
const statusOptions = [{ value: "DRAFT", label: "草稿" }, { value: "PENDING_APPROVAL", label: "审批中" }, { value: "PENDING_SUB_PERMITS", label: "等待专项票" }, { value: "RETURNED", label: "已退回" }, { value: "APPROVED", label: "已批准" }, { value: "IN_PROGRESS", label: "作业中" }, { value: "PENDING_CLOSE", label: "待关总票" }, { value: "CLOSED", label: "已关闭" }];
const loading = ref(false); const saving = ref(false); const actionLoading = ref(false); const editorVisible = ref(false); const detailVisible = ref(false); const approvalVisible = ref(false); const sampleVisible = ref(false); const closeVisible = ref(false);
const editingId = ref<number>(); const editingPermitType = ref<EhsPermitType>("WORK_PERMIT"); const detail = ref<EhsPermit>(); const actionPermitId = ref<number>(); const formRef = ref<FormInstance>(); const departments = ref<EhsDepartmentOption[]>([]); const viewMode = ref("all");
const stats = reactive<Record<string, number>>({}); const page = reactive({ total: 0, records: [] as EhsPermit[] }); const query = reactive({ pageNo: 1, pageSize: 10, keyword: "", status: "" });
const form = reactive<EhsPermitSavePayload>(emptyForm());
const approvalForm = reactive({ action: "APPROVE" as "APPROVE" | "RETURN", comment: "同意" });
const sampleForm = reactive({ sampledAt: nowText(), position: "", oxygen: 20.9, lel: 0, carbonMonoxide: 0, hydrogenSulfide: 0, recorder: "", signature: "" });
const closeForm = reactive({ outcome: "NORMAL", actualEndAt: nowText(), signature: "", note: "", photoText: "" });
const rules: FormRules = { approvalRouteId: [{ required: true, message: "请选择作业所属部门", trigger: "change" }], title: [{ required: true, message: "请填写作业名称", trigger: "blur" }], location: [{ required: true, message: "请填写作业地点", trigger: "blur" }], workContent: [{ required: true, message: "请填写作业内容", trigger: "blur" }], plannedStartAt: [{ required: true, message: "请选择计划开始时间", trigger: "change" }], plannedEndAt: [{ required: true, message: "请选择计划结束时间", trigger: "change" }] };

function emptyForm(): EhsPermitSavePayload { const start = new Date(Date.now() + 3600000); const end = new Date(Date.now() + 9 * 3600000); return { approvalRouteId: undefined, title: "", contractorName: "", region: "", location: "", workContent: "", plannedStartAt: isoLocal(start), plannedEndAt: isoLocal(end), requiredPermitTypes: [], riskItems: [{ risk: "", control: "" }], safetyNotice: { content: "" }, operationSteps: [], signatures: [], attachments: [], remark: "" }; }
function resetForm() { Object.assign(form, emptyForm()); form.riskItems = [{ risk: "", control: "" }]; form.safetyNotice = { content: "" }; form.operationSteps = []; form.signatures = []; form.attachments = []; editingId.value = undefined; editingPermitType.value = "WORK_PERMIT"; }
function openCreate() { resetForm(); editorVisible.value = true; }
async function openEdit(row: EhsPermit) { const full = row.children ? row : await getEhsPermitApi(row.id); editingId.value = full.id; editingPermitType.value = full.permitType; Object.assign(form, { ...full, riskItems: structuredClone(full.riskItems || []), safetyNotice: { ...(full.safetyNotice || {}) }, operationSteps: structuredClone(full.operationSteps || []), signatures: structuredClone(full.signatures || []), attachments: structuredClone(full.attachments || []) }); editorVisible.value = true; }
async function openDetail(row: EhsPermit) { detail.value = await getEhsPermitApi(row.id); detailVisible.value = true; }
function addOperationStep() { form.operationSteps.push({ order: form.operationSteps.length + 1, risk: "", control: "", operator: "", confirmer: "" }); }

async function load() { loading.value = true; try { const params: Record<string, unknown> = { ...query }; if (viewMode.value === "pending") params.pendingMine = true; if (viewMode.value === "mine") params.mineOnly = true; const [result, metric] = await Promise.all([pageEhsPermitsApi(params), getEhsPermitStatsApi()]); page.total = result.total; page.records = result.records; Object.assign(stats, metric); } finally { loading.value = false; } }
function search() { query.pageNo = 1; void load(); }
function resetFilters() { query.keyword = ""; query.status = ""; search(); }
async function savePermit() { await formRef.value?.validate(); saving.value = true; try { const payload = structuredClone(form); if (editingId.value) await updateEhsPermitApi(editingId.value, payload); else await createEhsPermitApi(payload); ElMessage.success("作业票草稿已保存"); editorVisible.value = false; await load(); } finally { saving.value = false; } }
function openPublicPortal() { window.open("/ehs-public/apply", "_blank", "noopener,noreferrer"); }
async function submitPermit(row: EhsPermit) { await ElMessageBox.confirm(`提交 ${row.permitNo} 进入统一审批？`, "提交审批", { type: "warning" }); await submitEhsPermitApi(row.id); ElMessage.success("已提交审批并生成统一待办"); detailVisible.value = false; await load(); }
async function removePermit(row: EhsPermit) { await ElMessageBox.confirm(`确认删除草稿 ${row.permitNo}？`, "删除草稿", { type: "warning" }); await deleteEhsPermitApi(row.id); ElMessage.success("已删除"); await load(); }
function openApproval(row: EhsPermit) { actionPermitId.value = row.id; approvalForm.action = "APPROVE"; approvalForm.comment = "同意"; approvalVisible.value = true; }
async function confirmApproval() { if (!actionPermitId.value) return; if (approvalForm.action === "RETURN" && !approvalForm.comment.trim()) { ElMessage.warning("退回时必须填写原因"); return; } actionLoading.value = true; try { await approveEhsPermitApi(actionPermitId.value, approvalForm.action, approvalForm.comment); ElMessage.success("审批已处理"); approvalVisible.value = false; detail.value = await getEhsPermitApi(actionPermitId.value); await load(); } finally { actionLoading.value = false; } }
async function startPermit(row: EhsPermit) { await ElMessageBox.confirm("确认现场条件具备并开始作业？", "确认开工", { type: "warning" }); await startEhsPermitApi(row.id); ElMessage.success("已进入作业中"); detail.value = await getEhsPermitApi(row.id); await load(); }
async function saveSample() { if (!detail.value) return; if (!sampleForm.position || !sampleForm.recorder) { ElMessage.warning("请填写采样位置和记录人"); return; } actionLoading.value = true; try { await addEhsPermitSampleApi(detail.value.id, { ...sampleForm }); ElMessage.success("采样记录已保存"); sampleVisible.value = false; detail.value = await getEhsPermitApi(detail.value.id); } finally { actionLoading.value = false; } }
function openClose(row: EhsPermit) { actionPermitId.value = row.id; Object.assign(closeForm, { outcome: "NORMAL", actualEndAt: nowText(), signature: row.applicantName, note: "", photoText: "" }); closeVisible.value = true; }
async function confirmClose() { if (!actionPermitId.value) return; if (!closeForm.signature.trim()) { ElMessage.warning("请填写关单签名"); return; } const photos = closeForm.photoText.split(/\r?\n/).map((item) => item.trim()).filter(Boolean); if (photos.length > 6) { ElMessage.warning("照片链接最多 6 个"); return; } actionLoading.value = true; try { await closeEhsPermitApi(actionPermitId.value, { outcome: closeForm.outcome, actualEndAt: closeForm.actualEndAt, signature: closeForm.signature, note: closeForm.note, photos }); ElMessage.success("关单完成"); closeVisible.value = false; detail.value = await getEhsPermitApi(actionPermitId.value); await load(); } finally { actionLoading.value = false; } }

function permitTypeLabel(type: EhsPermitType) { return ({ WORK_PERMIT: "总作业票", HOT_WORK: "动火", HEIGHT_WORK: "高处", CONFINED_SPACE: "受限空间", ELECTRIC_WORK: "临时用电", LIFTING_WORK: "吊装", EXCAVATION_WORK: "动土" } as Record<string, string>)[type] || type; }
function statusLabel(value: string) { return statusOptions.find((item) => item.value === value)?.label || value; }
function statusType(value: string): "success" | "warning" | "danger" | "info" { if (value === "CLOSED") return "info"; if (["APPROVED", "IN_PROGRESS"].includes(value)) return "success"; if (value === "RETURNED") return "danger"; return "warning"; }
function actionLabel(value: string) { return ({ SUBMIT: "提交", APPROVE: "通过", RETURN: "退回" } as Record<string, string>)[value] || value; }
function formatTime(value?: string) { return value ? value.replace("T", " ").slice(0, 16) : "-"; }
function isoLocal(date: Date) { const p = (v: number) => String(v).padStart(2, "0"); return `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}:00`; }
function nowText() { return isoLocal(new Date()); }

onMounted(async () => { departments.value = await getEhsDepartmentsApi(); await load(); });
</script>

<style scoped>
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; min-height: 108px; margin-bottom: 16px; }
.filter-bar { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 0 8px; padding: 6px 12px 0; margin-bottom: 14px; border: 1px solid #e1e8f2; border-radius: 6px; background: #f8fafc; }
.filter-bar :deep(.el-input) { width: min(300px, 60vw); } .filter-bar :deep(.el-select) { width: 180px; }
.pagination-row { display: flex; justify-content: flex-end; padding-top: 14px; }
.permit-form :deep(.el-select), .permit-form :deep(.el-date-editor) { width: 100%; }
.dynamic-row { display: grid; align-items: center; gap: 8px; margin-bottom: 8px; } .risk-row { grid-template-columns: 1fr 1.7fr 34px; } .step-row { grid-template-columns: 90px 1.3fr 1.5fr 110px 110px 34px; } .signature-row, .attachment-row { grid-template-columns: 1fr 1.8fr 34px; }
.remark-field { margin-top: 18px; }
.drawer-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.detail-section { margin-top: 22px; } .detail-section h3 { margin: 0 0 10px; color: #20344f; font-size: 15px; letter-spacing: 0; }
.notice-text { min-height: 54px; padding: 12px; margin: 0; white-space: pre-wrap; border: 1px solid #e1e8f2; border-radius: 6px; background: #f8fafc; color: #40546f; }
.sample-grid { display: grid; grid-template-columns: 1fr 1fr; }
@media (max-width: 1050px) { .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .step-row { grid-template-columns: 80px 1fr 1fr 34px; } }
@media (max-width: 650px) { .metric-grid { grid-template-columns: 1fr; } .filter-bar { display: block; } .dynamic-row, .sample-grid { grid-template-columns: 1fr; } }
</style>
