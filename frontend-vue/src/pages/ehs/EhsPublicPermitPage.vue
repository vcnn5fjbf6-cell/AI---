<template>
  <div class="public-shell">
    <header class="public-header">
      <div class="header-inner">
        <div class="brand"><el-icon><SafetyCertificate /></el-icon><span>EHS 作业许可</span></div>
        <span class="header-mode">{{ pageTitle }}</span>
      </div>
    </header>

    <main class="public-main">
      <el-result v-if="fatalError" icon="error" title="访问失败" :sub-title="fatalError">
        <template #extra><el-button type="primary" @click="goApply">返回申请入口</el-button></template>
      </el-result>

      <el-result v-else-if="actionComplete" icon="success" title="审批已完成" sub-title="本审批链接已使用，无需重复提交">
        <template #extra><el-button type="primary" @click="goApply">返回申请入口</el-button></template>
      </el-result>

      <template v-else-if="isApply">
        <div class="page-heading"><div><h1>作业许可申请</h1><p>提交后自动进入所属部门审批流程</p></div><el-tag type="warning" effect="plain">外部入口</el-tag></div>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="108px" class="public-form">
          <section class="form-section">
            <h2>申请信息</h2>
            <el-row :gutter="16">
              <el-col :xs="24" :sm="12"><el-form-item label="申请人" prop="applicantName"><el-input v-model="form.applicantName" maxlength="128" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item label="联系电话" prop="applicantPhone"><el-input v-model="form.applicantPhone" maxlength="32" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item label="申请单位"><el-input v-model="form.applicantCompany" maxlength="255" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item label="施工单位"><el-input v-model="form.contractorName" maxlength="255" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="作业部门" prop="approvalRouteId"><el-select v-model="form.approvalRouteId" filterable placeholder="选择作业所属部门"><el-option v-for="item in departments" :key="item.approvalRouteId" :label="item.label" :value="item.approvalRouteId" /></el-select></el-form-item></el-col>
            </el-row>
          </section>

          <section class="form-section">
            <h2>作业内容</h2>
            <el-row :gutter="16">
              <el-col :span="24"><el-form-item label="作业名称" prop="title"><el-input v-model="form.title" maxlength="255" /></el-form-item></el-col>
              <el-col :xs="24" :sm="8"><el-form-item label="作业区域"><el-input v-model="form.region" maxlength="128" /></el-form-item></el-col>
              <el-col :xs="24" :sm="16"><el-form-item label="具体地点" prop="location"><el-input v-model="form.location" maxlength="255" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="作业内容" prop="workContent"><el-input v-model="form.workContent" type="textarea" :rows="4" maxlength="3000" show-word-limit /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item label="计划开始" prop="plannedStartAt"><el-date-picker v-model="form.plannedStartAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col>
              <el-col :xs="24" :sm="12"><el-form-item label="计划结束" prop="plannedEndAt"><el-date-picker v-model="form.plannedEndAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-col>
              <el-col :span="24"><el-form-item label="专项作业票"><el-checkbox-group v-model="form.requiredPermitTypes"><el-checkbox v-for="item in specialPermitTypes" :key="item.value" :value="item.value">{{ item.label }}</el-checkbox></el-checkbox-group></el-form-item></el-col>
            </el-row>
          </section>

          <section class="form-section">
            <div class="section-title"><h2>风险与控制措施</h2><el-button :icon="Plus" @click="addRisk">添加</el-button></div>
            <div v-for="(item, index) in form.riskItems" :key="index" class="risk-row"><el-input v-model="item.risk" placeholder="风险描述" /><el-input v-model="item.control" placeholder="控制措施" /><el-button :icon="Delete" circle title="删除" @click="form.riskItems.splice(index, 1)" /></div>
            <el-form-item label="安全告知"><el-input v-model="form.safetyNotice.content" type="textarea" :rows="4" maxlength="3000" /></el-form-item>
          </section>

          <input v-model="form.website" class="honeypot" tabindex="-1" autocomplete="off" />
          <div class="submit-bar"><el-button type="primary" size="large" :loading="saving" @click="submitApplication">提交作业申请</el-button></div>
        </el-form>
      </template>

      <template v-else-if="permit">
        <div class="page-heading"><div><h1>{{ isReview ? "作业票审批" : "作业许可进度" }}</h1><p>{{ permit.permitNo }}</p></div><el-tag :type="statusType(permit.status)" effect="light">{{ statusLabel(permit.status) }}</el-tag></div>
        <section class="detail-band">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="作业名称" :span="2">{{ permit.title }}</el-descriptions-item>
            <el-descriptions-item label="申请人">{{ permit.applicantName }}</el-descriptions-item><el-descriptions-item label="申请单位">{{ permit.applicantCompany || "-" }}</el-descriptions-item>
            <el-descriptions-item label="作业部门" :span="2">{{ permit.deptName || "-" }}</el-descriptions-item>
            <el-descriptions-item label="作业地点" :span="2">{{ permit.region ? `${permit.region} / ` : "" }}{{ permit.location }}</el-descriptions-item>
            <el-descriptions-item label="计划开始">{{ formatTime(permit.plannedStartAt) }}</el-descriptions-item><el-descriptions-item label="计划结束">{{ formatTime(permit.plannedEndAt) }}</el-descriptions-item>
            <el-descriptions-item label="当前节点">{{ permit.currentLevelName || "-" }}</el-descriptions-item><el-descriptions-item label="当前审批人">{{ permit.currentApproverName || "-" }}</el-descriptions-item>
            <el-descriptions-item label="作业内容" :span="2">{{ permit.workContent }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section v-if="permit.children.length" class="detail-band">
          <h2>专项作业票</h2>
          <el-table :data="permit.children" border>
            <el-table-column prop="permitNo" label="票号" min-width="170" /><el-table-column prop="permitType" label="类型" width="120"><template #default="{ row }">{{ permitTypeLabel(row.permitType) }}</template></el-table-column><el-table-column prop="currentLevelName" label="当前节点" min-width="130"><template #default="{ row }">{{ row.currentLevelName || "-" }}</template></el-table-column><el-table-column prop="status" label="状态" width="120"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column><el-table-column v-if="!isReview" label="操作" width="190"><template #default="{ row }"><el-button v-if="row.canEdit" link type="primary" @click="openEdit(row)">修改</el-button><el-button v-if="row.canSubmit" link type="success" @click="resubmit(row)">重新提交</el-button><el-button v-if="row.canSample" link type="primary" @click="openSample(row)">采样</el-button><el-button v-if="row.canClose" link type="warning" @click="openClose(row)">关单</el-button></template></el-table-column>
          </el-table>
        </section>

        <section class="detail-band"><h2>风险与控制措施</h2><el-table :data="permit.riskItems" border><el-table-column type="index" width="55" /><el-table-column prop="risk" label="风险" min-width="180" /><el-table-column prop="control" label="控制措施" min-width="240" /></el-table><p class="notice">{{ permit.safetyNotice?.content || "未填写安全告知" }}</p></section>
        <section class="detail-band"><h2>审批记录</h2><el-timeline v-if="permit.approvalRecords.length"><el-timeline-item v-for="(record, index) in permit.approvalRecords" :key="`${record.nodeNo}-${index}`" :timestamp="formatTime(record.operatedAt)" placement="top"><strong>{{ record.nodeName }} · {{ actionLabel(record.action) }}</strong><p>{{ record.approverName || "系统" }}：{{ record.approvalComment || "-" }}</p></el-timeline-item></el-timeline><el-empty v-else description="尚无审批记录" :image-size="56" /></section>

        <div v-if="isReview" class="action-band"><el-segmented v-model="approval.action" :options="approvalOptions" /><el-input v-model="approval.comment" type="textarea" :rows="3" placeholder="审批意见" /><el-button type="primary" size="large" :loading="saving" :disabled="!permit.canApprove" @click="submitApproval">确认审批</el-button></div>
        <div v-else class="action-band applicant-actions"><el-button v-if="permit.canEdit" :icon="Edit" @click="openEdit(permit)">修改</el-button><el-button v-if="permit.canSubmit" type="primary" :icon="Promotion" @click="resubmit(permit)">重新提交</el-button><el-button v-if="permit.canStart" type="success" :icon="VideoPlay" @click="startWork">确认开工</el-button><el-button v-if="permit.canClose" type="warning" :icon="CircleCheck" @click="openClose(permit)">关单</el-button></div>
      </template>
    </main>

    <el-dialog v-model="editVisible" title="修改作业票" width="min(760px, 94vw)"><el-form :model="form" label-width="100px"><el-form-item label="作业部门"><el-select v-model="form.approvalRouteId" filterable><el-option v-for="item in departments" :key="item.approvalRouteId" :label="item.label" :value="item.approvalRouteId" /></el-select></el-form-item><el-form-item label="作业名称"><el-input v-model="form.title" /></el-form-item><el-form-item label="作业地点"><el-input v-model="form.location" /></el-form-item><el-form-item label="作业内容"><el-input v-model="form.workContent" type="textarea" :rows="4" /></el-form-item><el-form-item label="计划时间"><el-date-picker v-model="editRange" type="datetimerange" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-form><template #footer><el-button @click="editVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button></template></el-dialog>
    <el-dialog v-model="sampleVisible" title="受限空间气体采样" width="min(620px, 94vw)"><el-form label-width="92px"><el-form-item label="采样时间"><el-date-picker v-model="sample.sampledAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="采样位置"><el-input v-model="sample.position" /></el-form-item><div class="sample-grid"><el-form-item label="氧气(%)"><el-input-number v-model="sample.oxygen" :precision="2" /></el-form-item><el-form-item label="LEL(%)"><el-input-number v-model="sample.lel" :precision="2" /></el-form-item><el-form-item label="CO"><el-input-number v-model="sample.carbonMonoxide" :precision="2" /></el-form-item><el-form-item label="H₂S"><el-input-number v-model="sample.hydrogenSulfide" :precision="2" /></el-form-item></div><el-form-item label="记录人"><el-input v-model="sample.recorder" /></el-form-item><el-form-item label="签名"><el-input v-model="sample.signature" /></el-form-item></el-form><template #footer><el-button @click="sampleVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveSample">保存</el-button></template></el-dialog>
    <el-dialog v-model="closeVisible" title="作业票关单" width="min(560px, 94vw)"><el-form label-width="92px"><el-form-item label="关单结果"><el-select v-model="closure.outcome"><el-option label="正常完成" value="NORMAL" /><el-option label="取消作业" value="CANCELLED" /><el-option label="异常结束" value="ABNORMAL" /></el-select></el-form-item><el-form-item label="实际结束"><el-date-picker v-model="closure.actualEndAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item><el-form-item label="关单签名"><el-input v-model="closure.signature" /></el-form-item><el-form-item label="关单说明"><el-input v-model="closure.note" type="textarea" :rows="3" /></el-form-item></el-form><template #footer><el-button @click="closeVisible=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveClose">确认关单</el-button></template></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import { CircleCheck, Delete, Edit, Plus, Promotion, VideoPlay, WarningFilled as SafetyCertificate } from "@element-plus/icons-vue";
import { addPublicEhsSampleApi, approvePublicEhsPermitApi, closePublicEhsPermitApi, createPublicEhsPermitApi, getPublicEhsPermitApi, getPublicEhsReviewApi, listPublicEhsDepartmentsApi, startPublicEhsPermitApi, submitPublicEhsPermitApi, updatePublicEhsPermitApi } from "../../api/ehs-public";
import type { EhsDepartmentOption, EhsPublicPermit, EhsPublicPermitSavePayload, EhsPermitType } from "../../types/ehs";

const route = useRoute();
const isApply = computed(() => route.name === "EhsPublicApply");
const isReview = computed(() => route.name === "EhsPublicReview");
const pageTitle = computed(() => isApply.value ? "外部申请" : isReview.value ? "免登录审批" : "申请进度");
const departments = ref<EhsDepartmentOption[]>([]); const permit = ref<EhsPublicPermit>(); const fatalError = ref(""); const saving = ref(false); const formRef = ref<FormInstance>();
const actionComplete = ref(false);
const editVisible = ref(false); const sampleVisible = ref(false); const closeVisible = ref(false); const actionPermitId = ref<number>();
const specialPermitTypes: Array<{ value: EhsPermitType; label: string }> = [{ value: "HOT_WORK", label: "动火" }, { value: "HEIGHT_WORK", label: "高处" }, { value: "CONFINED_SPACE", label: "受限空间" }, { value: "ELECTRIC_WORK", label: "临时用电" }, { value: "LIFTING_WORK", label: "吊装" }, { value: "EXCAVATION_WORK", label: "动土" }];
const form = reactive<EhsPublicPermitSavePayload>(emptyForm()); const editRange = ref<[string, string]>();
const approval = reactive({ action: "APPROVE" as "APPROVE" | "RETURN", comment: "同意" }); const approvalOptions = [{ label: "通过", value: "APPROVE" }, { label: "退回", value: "RETURN" }];
const sample = reactive({ sampledAt: nowText(), position: "", oxygen: 20.9, lel: 0, carbonMonoxide: 0, hydrogenSulfide: 0, recorder: "", signature: "" });
const closure = reactive({ outcome: "NORMAL", actualEndAt: nowText(), signature: "", note: "", photos: [] as string[] });
const rules: FormRules = { approvalRouteId: [{ required: true, message: "请选择作业所属部门", trigger: "change" }], applicantName: [{ required: true, message: "请填写申请人", trigger: "blur" }], applicantPhone: [{ required: true, message: "请填写联系电话", trigger: "blur" }], title: [{ required: true, message: "请填写作业名称", trigger: "blur" }], location: [{ required: true, message: "请填写作业地点", trigger: "blur" }], workContent: [{ required: true, message: "请填写作业内容", trigger: "blur" }], plannedStartAt: [{ required: true, message: "请选择计划开始时间", trigger: "change" }], plannedEndAt: [{ required: true, message: "请选择计划结束时间", trigger: "change" }] };

function token() { return new URLSearchParams(location.hash.replace(/^#/, "")).get("token") || ""; }
function emptyForm(): EhsPublicPermitSavePayload { const start=new Date(Date.now()+3600000), end=new Date(Date.now()+9*3600000); return { approvalRouteId: undefined, applicantName: "", applicantPhone: "", applicantCompany: "", title: "", contractorName: "", region: "", location: "", workContent: "", plannedStartAt: isoLocal(start), plannedEndAt: isoLocal(end), requiredPermitTypes: [], riskItems: [{ risk: "", control: "" }], safetyNotice: { content: "" }, operationSteps: [], signatures: [], attachments: [], remark: "", website: "" }; }
function addRisk() { form.riskItems.push({ risk: "", control: "" }); }
async function submitApplication() { await formRef.value?.validate(); saving.value=true; try { const result=await createPublicEhsPermitApi(structuredClone(form)); location.replace(`/ehs-public/permit#token=${result.accessToken}`); } catch (error) { ElMessage.error(error instanceof Error ? error.message : "提交失败"); } finally { saving.value=false; } }
async function loadPermit() { const access=token(); if(!access){ fatalError.value="链接中缺少访问凭证"; return; } try { permit.value=isReview.value ? await getPublicEhsReviewApi(access) : await getPublicEhsPermitApi(access); } catch(error){ fatalError.value=error instanceof Error ? error.message : "访问链接无效或已过期"; } }
function openEdit(row:EhsPublicPermit) { if(!permit.value)return; actionPermitId.value=row.id; Object.assign(form, emptyForm(), { ...row, applicantName:permit.value.applicantName, applicantPhone:permit.value.applicantPhone || "", applicantCompany:permit.value.applicantCompany || "", riskItems:structuredClone(row.riskItems||[]), safetyNotice:{...(row.safetyNotice||{})}, operationSteps:structuredClone(row.operationSteps||[]), signatures:[], attachments:[] }); editRange.value=[row.plannedStartAt,row.plannedEndAt]; editVisible.value=true; }
async function saveEdit() { if(!actionPermitId.value)return; if(editRange.value){ form.plannedStartAt=editRange.value[0]; form.plannedEndAt=editRange.value[1]; } saving.value=true; try { await updatePublicEhsPermitApi(actionPermitId.value, structuredClone(form), token()); ElMessage.success("已保存"); editVisible.value=false; await loadPermit(); } catch(error){ ElMessage.error(error instanceof Error?error.message:"保存失败"); } finally { saving.value=false; } }
async function resubmit(row:EhsPublicPermit) { try{await ElMessageBox.confirm(`重新提交 ${row.permitNo}？`,"提交审批",{type:"warning"});}catch{return;} saving.value=true;try{await submitPublicEhsPermitApi(row.id,token());ElMessage.success("已重新提交");await loadPermit();}catch(error){ElMessage.error(error instanceof Error?error.message:"重新提交失败");}finally{saving.value=false;} }
async function startWork() { if(!permit.value)return;try{await ElMessageBox.confirm("确认现场条件具备并开始作业？","确认开工",{type:"warning"});}catch{return;} saving.value=true;try{await startPublicEhsPermitApi(permit.value.id,token());ElMessage.success("已确认开工");await loadPermit();}catch(error){ElMessage.error(error instanceof Error?error.message:"确认开工失败");}finally{saving.value=false;} }
function openSample(row:EhsPublicPermit) { actionPermitId.value=row.id; Object.assign(sample,{sampledAt:nowText(),position:"",oxygen:20.9,lel:0,carbonMonoxide:0,hydrogenSulfide:0,recorder:permit.value?.applicantName||"",signature:""}); sampleVisible.value=true; }
async function saveSample() { if(!actionPermitId.value||!sample.position||!sample.recorder){ElMessage.warning("请填写采样位置和记录人");return;} saving.value=true; try{await addPublicEhsSampleApi(actionPermitId.value,{...sample},token());ElMessage.success("采样记录已保存");sampleVisible.value=false;await loadPermit();}catch(error){ElMessage.error(error instanceof Error?error.message:"采样记录保存失败");}finally{saving.value=false;} }
function openClose(row:EhsPublicPermit) { actionPermitId.value=row.id; Object.assign(closure,{outcome:"NORMAL",actualEndAt:nowText(),signature:permit.value?.applicantName||"",note:"",photos:[]}); closeVisible.value=true; }
async function saveClose() { if(!actionPermitId.value||!closure.signature.trim()){ElMessage.warning("请填写关单签名");return;} saving.value=true; try{await closePublicEhsPermitApi(actionPermitId.value,{...closure},token());ElMessage.success("关单完成");closeVisible.value=false;await loadPermit();}catch(error){ElMessage.error(error instanceof Error?error.message:"关单失败");}finally{saving.value=false;} }
async function submitApproval() { if(approval.action==="RETURN"&&!approval.comment.trim()){ElMessage.warning("退回时必须填写原因");return;} saving.value=true; try{await approvePublicEhsPermitApi(token(),approval.action,approval.comment);permit.value=undefined;actionComplete.value=true;ElMessage.success("审批已处理");}catch(error){ElMessage.error(error instanceof Error?error.message:"审批失败");}finally{saving.value=false;} }
function goApply(){location.href="/ehs-public/apply";}
function statusLabel(value:string){return ({DRAFT:"草稿",PENDING_APPROVAL:"审批中",PENDING_SUB_PERMITS:"等待专项票",RETURNED:"已退回",APPROVED:"已批准",IN_PROGRESS:"作业中",PENDING_CLOSE:"待关总票",CLOSED:"已关闭"} as Record<string,string>)[value]||value;}
function statusType(value:string):"success"|"warning"|"danger"|"info"{if(value==="CLOSED")return"info";if(["APPROVED","IN_PROGRESS"].includes(value))return"success";if(value==="RETURNED")return"danger";return"warning";}
function permitTypeLabel(type:string){return ({WORK_PERMIT:"总作业票",HOT_WORK:"动火",HEIGHT_WORK:"高处",CONFINED_SPACE:"受限空间",ELECTRIC_WORK:"临时用电",LIFTING_WORK:"吊装",EXCAVATION_WORK:"动土"} as Record<string,string>)[type]||type;}
function actionLabel(value:string){return ({SUBMIT:"提交",APPROVE:"通过",RETURN:"退回"} as Record<string,string>)[value]||value;}
function formatTime(value?:string){return value?value.replace("T"," ").slice(0,16):"-";}
function isoLocal(date:Date){const p=(v:number)=>String(v).padStart(2,"0");return `${date.getFullYear()}-${p(date.getMonth()+1)}-${p(date.getDate())}T${p(date.getHours())}:${p(date.getMinutes())}:00`;}
function nowText(){return isoLocal(new Date());}

onMounted(async()=>{try{departments.value=await listPublicEhsDepartmentsApi();if(!isApply.value)await loadPermit();}catch(error){fatalError.value=error instanceof Error?error.message:"页面加载失败";}});
</script>

<style scoped>
.public-shell{min-height:100vh;background:#f3f6f9;color:#1d2c3d}.public-header{height:60px;background:#15344a;color:#fff;border-bottom:4px solid #2e9b74}.header-inner{width:min(1080px,calc(100% - 32px));height:100%;margin:0 auto;display:flex;align-items:center;justify-content:space-between}.brand{display:flex;align-items:center;gap:10px;font-size:18px;font-weight:700}.brand .el-icon{font-size:24px;color:#7fe0bc}.header-mode{font-size:13px;color:#d7e4ec}.public-main{width:min(1080px,calc(100% - 32px));margin:0 auto;padding:28px 0 56px}.page-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;margin-bottom:22px}.page-heading h1{margin:0;font-size:28px;line-height:1.2;letter-spacing:0}.page-heading p{margin:7px 0 0;color:#66788a;font-size:14px}.public-form,.detail-band,.action-band{background:#fff;border:1px solid #dce4eb;border-radius:6px}.form-section,.detail-band{padding:22px 24px;border-bottom:1px solid #e6ebf0}.form-section:last-of-type{border-bottom:0}.form-section h2,.detail-band h2{margin:0 0 18px;font-size:16px;letter-spacing:0;color:#20384b}.section-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:14px}.section-title h2{margin:0}.public-form :deep(.el-select),.public-form :deep(.el-date-editor){width:100%}.risk-row{display:grid;grid-template-columns:1fr 1.5fr 34px;gap:8px;margin-bottom:10px}.submit-bar{display:flex;justify-content:flex-end;padding:18px 24px;border-top:1px solid #e6ebf0}.detail-band{margin-bottom:14px}.notice{padding:12px;margin:14px 0 0;background:#f4f8f6;border-left:3px solid #2e9b74;white-space:pre-wrap}.action-band{position:sticky;bottom:0;display:grid;grid-template-columns:auto minmax(220px,1fr) auto;gap:12px;align-items:center;padding:16px;margin-top:14px;box-shadow:0 -6px 20px rgba(21,52,74,.08)}.applicant-actions{display:flex;justify-content:flex-end}.sample-grid{display:grid;grid-template-columns:1fr 1fr}.honeypot{position:absolute!important;left:-9999px!important;width:1px;height:1px;opacity:0}.public-main :deep(.el-descriptions__label){width:110px}.public-main :deep(.el-timeline-item__content p){margin:6px 0;color:#657789}.public-main :deep(.el-result){background:#fff;border:1px solid #dce4eb;border-radius:6px}.public-main :deep(.el-table){width:100%}
@media(max-width:700px){.public-main{width:min(100% - 20px,1080px);padding-top:18px}.header-inner{width:calc(100% - 24px)}.page-heading h1{font-size:23px}.form-section,.detail-band{padding:18px 14px}.public-form :deep(.el-form-item){display:block}.public-form :deep(.el-form-item__label){justify-content:flex-start}.risk-row,.sample-grid{grid-template-columns:1fr}.action-band{position:static;grid-template-columns:1fr}.applicant-actions{display:grid}.public-main :deep(.el-descriptions__body .el-descriptions__table){table-layout:fixed}.public-main :deep(.el-descriptions__cell){display:block;width:100%}.public-main :deep(.el-descriptions__label),.public-main :deep(.el-descriptions__content){display:block!important;width:auto!important}.submit-bar .el-button{width:100%}}
</style>
