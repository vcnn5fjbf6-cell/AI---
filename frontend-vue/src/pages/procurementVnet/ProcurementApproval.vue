<template>
  <VnetPage title="审批操作">
    <template #actions>
      <el-button :loading="loading" @click="loadTableData">刷新</el-button>
    </template>

    <VnetPanel title="待审批筛选">
      <el-form :inline="true" :model="queryModel" class="vnet-search-form">
        <el-form-item label="单号">
          <el-input v-model="queryModel.reqNo" clearable placeholder="输入申请单号" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="queryModel.reqTitle" clearable placeholder="输入申请标题" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </VnetPanel>

    <VnetPanel title="待审批列表">
      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="reqNo" label="申请单号" min-width="170" />
        <el-table-column prop="reqTitle" label="申请标题" min-width="220" />
        <el-table-column prop="reqType" label="汇总类型" width="110" />
        <el-table-column prop="deptName" label="部门" min-width="130" />
        <el-table-column label="申请人" width="130">
          <template #default="{ row }">{{ applicantDisplay(row) }}</template>
        </el-table-column>
        <el-table-column label="当前节点" width="130">
          <template #default="{ row }">{{ nodeName(row.currentNodeNo) }}</template>
        </el-table-column>
        <el-table-column label="当前金额" width="130">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="审批状态" width="110">
          <template #default="{ row }">
            <el-tag :type="approvalTagType(row.approvalStatus)" effect="light">
              {{ approvalStatusText(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" min-width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
              <el-button link type="primary" @click="openRecords(row)">记录</el-button>
              <el-button link type="warning" @click="openApproval(row)">审批</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager-box">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="queryModel.pageNo"
          :page-size="queryModel.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </VnetPanel>

    <el-dialog v-model="approvalVisible" title="审批处理" width="1040px">
      <div v-loading="approvalDetailLoading">
        <el-descriptions v-if="approvalTarget" :column="2" border class="dialog-summary">
          <el-descriptions-item label="申请单号">{{ approvalTarget.reqNo }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ approvalNodeName(approvalDetail || approvalTarget) }}</el-descriptions-item>
          <el-descriptions-item label="申请标题" :span="2">{{ approvalTarget.reqTitle }}</el-descriptions-item>
          <el-descriptions-item label="提交者">{{ applicantDisplay(approvalDetail || approvalTarget) }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime((approvalDetail || approvalTarget).submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ approvalTarget.deptName || "-" }}</el-descriptions-item>
          <el-descriptions-item label="收货城市">{{ approvalTarget.receivingCity || "-" }}</el-descriptions-item>
          <el-descriptions-item v-if="approvalTarget.actingDelegated" label="管理员代办" :span="2">
            {{ actingDelegationText(approvalTarget) }}
          </el-descriptions-item>
          <el-descriptions-item :label="totalAmountLabel(approvalDetail || approvalTarget)">
            {{ formatMoney((approvalDetail || approvalTarget).totalAmount) }}
          </el-descriptions-item>
          <el-descriptions-item
            v-if="usesSupplierQuotePrice(approvalDetail || approvalTarget)"
            label="原参考金额"
          >
            {{ formatMoney((approvalDetail || approvalTarget).referenceTotalAmount) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="isOverPriceReview" label="复核时限" :span="2">
            {{ overPriceDeadlineText }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="approvalTarget" class="approval-flow">
          <div
            v-for="node in flowNodesForRequest(approvalTarget, approvalDialogRecords)"
            :key="node.nodeNo"
            class="flow-node"
            :class="flowNodeClass(approvalTarget, approvalDialogRecords, node.nodeNo)"
          >
            <div class="flow-dot">{{ node.nodeNo }}</div>
            <div class="flow-content">
              <div class="flow-title-row">
                <strong>{{ node.name }}</strong>
                <el-tag size="small" :type="flowNodeTagType(approvalTarget, approvalDialogRecords, node.nodeNo)" effect="light">
                  {{ flowNodeState(approvalTarget, approvalDialogRecords, node.nodeNo) }}
                </el-tag>
              </div>
              <div v-if="latestRecordForNode(approvalDialogRecords, node.nodeNo)" class="flow-meta">
                {{ flowRecordMeta(latestRecordForNode(approvalDialogRecords, node.nodeNo)) }}
              </div>
            </div>
          </div>
        </div>

        <div v-if="canSmartHoldItems" class="smart-hold-box">
          <div class="smart-hold-head">
            <div>
              <strong>异常项快速处理</strong>
              <span>输入处理意见，系统会自动标记命中的明细，其余明细继续按通过流转。</span>
            </div>
            <el-tag type="warning" effect="light">试用</el-tag>
          </div>
          <div class="smart-hold-actions">
            <el-input
              v-model="smartHoldForm.text"
              type="textarea"
              :rows="2"
              placeholder="例如：阳光板不行，请确认是否阻燃；盲板数量再确认是否够用，剩下的同意"
            />
            <div class="smart-hold-buttons">
              <el-button type="warning" @click="applySmartHoldText">解析并标记异常项</el-button>
              <el-button @click="clearSmartHoldMarks">清空标记</el-button>
            </div>
          </div>
          <div v-if="smartHoldForm.summary" class="smart-hold-result">{{ smartHoldForm.summary }}</div>
        </div>

        <el-table v-if="approvalDetail" :data="approvalItems" stripe class="detail-table">
          <el-table-column prop="lineNo" label="行号" width="70" />
          <el-table-column prop="itemName" label="商品" min-width="160" />
          <el-table-column prop="reqType" label="采购类型" min-width="170" show-overflow-tooltip />
          <el-table-column prop="specModel" label="规格型号" min-width="130" />
          <el-table-column prop="qty" label="数量" width="80" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column v-if="isOverPriceReview" label="提交者/工单" min-width="170">
            <template #default="{ row }">
              <div>{{ row.sourceApplicantName || applicantDisplay(approvalDetail) }}</div>
              <small>{{ row.sourceRequestNo || approvalDetail.reqNo }}</small>
            </template>
          </el-table-column>
          <el-table-column v-if="isOverPriceReview" label="中标单位" min-width="150">
            <template #default="{ row }">{{ row.awardedSupplierName || "-" }}</template>
          </el-table-column>
          <el-table-column v-if="isOverPriceReview" label="参考/中标单价" width="160">
            <template #default="{ row }">
              {{ formatMoney(row.referenceUnitPrice) }} / {{ formatMoney(row.awardedUnitPrice || row.unitPrice) }}
            </template>
          </el-table-column>
          <el-table-column v-if="isOverPriceReview" label="超价差额" width="150">
            <template #default="{ row }">
              <el-tag v-if="row.overPriceReviewRequired" type="danger" effect="light">
                +{{ formatMoney(row.overPriceLineDifference) }} / +{{ formatPercent(row.overPriceRatePercent) }}
              </el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column :label="lineAmountLabel(approvalDetail)" width="130">
            <template #default="{ row }">{{ formatMoney(row.lineAmount || row.qty * row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column v-if="usesSupplierQuotePrice(approvalDetail)" label="原参考金额" width="130">
            <template #default="{ row }">{{ formatMoney(row.referenceLineAmount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="itemStatusTagType(row.status)" effect="plain">
                {{ itemStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column v-if="canRejectItems" label="单项驳回" width="110">
            <template #default="{ row }">
              <el-switch
                v-model="row.rejected"
                :disabled="row.status !== 'ACTIVE' || (isOverPriceReview && !row.overPriceReviewRequired)"
              />
            </template>
          </el-table-column>
          <el-table-column v-if="canRejectItems" label="采购原因/驳回原因" min-width="240">
            <template #default="{ row }">
              <el-input
                v-if="row.rejected && row.status !== 'REJECTED'"
                v-model="row.rejectReason"
                clearable
                placeholder="驳回该商品时必填"
              />
              <span v-else>{{ itemReasonText(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column v-else prop="remark" label="备注/原因" min-width="200" />
        </el-table>

        <el-form ref="approvalFormRef" :model="approvalForm" :rules="approvalRules" label-position="top" class="approval-form">
          <el-form-item label="审批动作" prop="action">
            <el-radio-group v-model="approvalForm.action">
              <el-radio-button value="APPROVE">{{ isOverPriceReview ? "通过并继续" : "通过" }}</el-radio-button>
              <el-radio-button v-if="canReturnApprovalTarget" value="RETURN">退回</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="needsReturnTarget" label="退回到节点">
            <el-select v-model="approvalForm.targetNodeNo" clearable placeholder="不选则退回申请人补充">
              <el-option
                v-for="node in previousNodeOptions"
                :key="node.nodeNo"
                :label="`${node.nodeNo} - ${node.name}`"
                :value="node.nodeNo"
              />
            </el-select>
          </el-form-item>
          <div class="inline-flow-status">
            <div class="flow-status-title">流程状态</div>
            <el-table :data="approvalDialogFlowRecordRows" border size="small" max-height="180">
              <el-table-column prop="nodeName" label="节点名称" min-width="140" />
              <el-table-column prop="endedAt" label="结束时间" min-width="150" />
              <el-table-column prop="handler" label="处理人" min-width="140" />
              <el-table-column prop="comment" label="处理意见" min-width="220" show-overflow-tooltip />
              <el-table-column prop="result" label="处理结果" width="110" />
            </el-table>
          </div>
          <el-form-item :label="isOverPriceReview ? '价格合理原因' : '审批意见'">
            <el-input
              v-model="approvalForm.approvalComment"
              type="textarea"
              :rows="4"
              :placeholder="isOverPriceReview ? '通过超价商品时填写市场价格、规格变化或紧急采购等具体原因' : '请输入审批意见'"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="approvalVisible = false">取消</el-button>
        <el-button @click="openFlowStatus(approvalTarget, approvalDialogRecords)">流程状态</el-button>
        <el-button :loading="submitLoading" @click="submitApproval('SAVE')">暂存</el-button>
        <el-button v-if="canTerminateApprovalTarget" type="danger" plain :loading="submitLoading" @click="submitApproval('TERMINATE')">终止</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitApproval()">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailVisible" title="采购申请详情" width="920px">
      <div v-loading="detailLoading">
        <el-descriptions v-if="currentDetail" :column="2" border>
          <el-descriptions-item label="申请单号">{{ currentDetail.reqNo }}</el-descriptions-item>
          <el-descriptions-item label="审批状态">{{ approvalStatusText(currentDetail.approvalStatus) }}</el-descriptions-item>
          <el-descriptions-item label="申请标题" :span="2">{{ currentDetail.reqTitle }}</el-descriptions-item>
          <el-descriptions-item label="汇总类型">{{ currentDetail.reqType }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ nodeName(currentDetail.currentNodeNo) }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ currentDetail.deptName || "-" }}</el-descriptions-item>
          <el-descriptions-item label="收货城市">{{ currentDetail.receivingCity || "-" }}</el-descriptions-item>
          <el-descriptions-item v-if="currentDetail.actingDelegated" label="管理员代办" :span="2">
            {{ actingDelegationText(currentDetail) }}
          </el-descriptions-item>
          <el-descriptions-item :label="totalAmountLabel(currentDetail)">{{ formatMoney(currentDetail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item v-if="usesSupplierQuotePrice(currentDetail)" label="原参考金额">
            {{ formatMoney(currentDetail.referenceTotalAmount) }}
          </el-descriptions-item>
          <el-descriptions-item label="需求日期">{{ currentDetail.needDate || "-" }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ currentDetail.submittedAt || "-" }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="currentDetail" class="approval-flow">
          <div
            v-for="node in flowNodesForRequest(currentDetail, detailRecords)"
            :key="node.nodeNo"
            class="flow-node"
            :class="flowNodeClass(currentDetail, detailRecords, node.nodeNo)"
          >
            <div class="flow-dot">{{ node.nodeNo }}</div>
            <div class="flow-content">
              <div class="flow-title-row">
                <strong>{{ node.name }}</strong>
                <el-tag size="small" :type="flowNodeTagType(currentDetail, detailRecords, node.nodeNo)" effect="light">
                  {{ flowNodeState(currentDetail, detailRecords, node.nodeNo) }}
                </el-tag>
              </div>
              <div v-if="latestRecordForNode(detailRecords, node.nodeNo)" class="flow-meta">
                {{ flowRecordMeta(latestRecordForNode(detailRecords, node.nodeNo)) }}
              </div>
            </div>
          </div>
        </div>

        <el-table v-if="currentDetail" :data="currentDetail.items || []" stripe class="detail-table">
          <el-table-column prop="lineNo" label="行号" width="70" />
          <el-table-column prop="itemCode" label="物料编码" min-width="140" />
          <el-table-column prop="itemName" label="商品名称" min-width="160" />
          <el-table-column prop="reqType" label="采购类型" min-width="170" show-overflow-tooltip />
          <el-table-column prop="specModel" label="规格型号" min-width="130" />
          <el-table-column prop="qty" label="数量" width="80" />
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column :label="lineAmountLabel(currentDetail)" width="130">
            <template #default="{ row }">{{ formatMoney(row.lineAmount || row.qty * row.unitPrice) }}</template>
          </el-table-column>
          <el-table-column v-if="usesSupplierQuotePrice(currentDetail)" label="原参考金额" width="130">
            <template #default="{ row }">{{ formatMoney(row.referenceLineAmount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="itemStatusTagType(row.status)" effect="plain">
                {{ itemStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注/驳回原因" min-width="180" />
          <el-table-column label="电商链接" min-width="120">
            <template #default="{ row }">
              <el-link v-if="row.ecommerceUrl" :href="normalizeUrl(row.ecommerceUrl)" target="_blank" type="primary">打开</el-link>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="detailRecords.length > 0" class="detail-records">
          <div class="detail-records-title">历史审批流</div>
          <el-timeline>
            <el-timeline-item
              v-for="record in sortedRecords(detailRecords)"
              :key="record.id"
              :timestamp="record.operatedAt || record.createdAt"
              :type="record.action === 'REJECT' ? 'danger' : record.action === 'RETURN' ? 'warning' : 'success'"
            >
              <strong>{{ record.nodeName || nodeName(record.nodeNo) }} - {{ actionText(record.action) }}</strong>
              <div class="record-meta">
                审批人ID：{{ record.approverUserId || "-" }} / 角色：{{ record.approverRoleCode || "-" }}
              </div>
              <div v-if="record.approvalComment" class="record-comment">{{ record.approvalComment }}</div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button @click="openFlowStatus(currentDetail, detailRecords)">流程状态</el-button>
        <el-button v-if="canApproveCurrentDetail" type="warning" @click="openCurrentDetailApproval">审批处理</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="recordsVisible" title="审批记录" width="680px">
      <div v-loading="recordsLoading">
        <el-timeline v-if="approvalRecords.length > 0">
          <el-timeline-item
            v-for="record in approvalRecords"
            :key="record.id"
            :timestamp="record.operatedAt || record.createdAt"
            :type="record.action === 'REJECT' ? 'danger' : record.action === 'RETURN' ? 'warning' : 'success'"
          >
            <strong>{{ record.nodeName || nodeName(record.nodeNo) }} - {{ actionText(record.action) }}</strong>
            <div class="record-meta">
              审批人ID：{{ record.approverUserId || "-" }} / 角色：{{ record.approverRoleCode || "-" }}
            </div>
            <div v-if="record.approvalComment" class="record-comment">{{ record.approvalComment }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无审批记录" />
      </div>
    </el-dialog>

    <el-dialog v-model="flowStatusVisible" title="流程状态" width="1180px" top="4vh" class="flow-status-dialog">
      <section class="flow-status-section">
        <div class="flow-status-title">流程记录</div>
        <el-table :data="flowRecordRows" border size="small">
          <el-table-column prop="index" label="序号" width="70" />
          <el-table-column prop="nodeName" label="节点名称" min-width="140" />
          <el-table-column prop="createdAt" label="创建时间" min-width="160" />
          <el-table-column prop="endedAt" label="结束时间" min-width="160" />
          <el-table-column prop="handler" label="处理人" min-width="140" />
          <el-table-column prop="comment" label="处理意见" min-width="220" show-overflow-tooltip />
          <el-table-column prop="result" label="处理结果" min-width="120" />
        </el-table>
      </section>
      <section class="flow-status-section">
        <div class="flow-status-title">流程图</div>
        <div class="flow-map">
          <div
            v-for="(node, index) in flowStatusNodes"
            :key="node.nodeNo"
            class="flow-map-node"
            :class="flowNodeClass(flowStatusRequest, flowStatusRecords, node.nodeNo)"
          >
            <div class="flow-map-index">{{ node.nodeNo }}</div>
            <strong>{{ node.name }}</strong>
            <span>{{ flowNodeState(flowStatusRequest, flowStatusRecords, node.nodeNo) }}</span>
            <small v-if="latestRecordForNode(flowStatusRecords, node.nodeNo)">
              {{ flowRecordMeta(latestRecordForNode(flowStatusRecords, node.nodeNo)) }}
            </small>
            <i v-if="index < flowStatusNodes.length - 1" class="flow-map-arrow">→</i>
          </div>
        </div>
      </section>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import type { FormInstance, FormRules } from "element-plus";
import { ElMessage, ElMessageBox } from "element-plus";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  approveRequestApi,
  getApprovalRecordsApi,
  getApprovalTasksPageApi,
  getWorkflowDetailApi
} from "../../api/workflow";
import type { ApprovalRecordVO } from "../../types/approval";
import type { PurchaseRequestItem, PurchaseRequestVO } from "../../types/purchase";
import { useAuthStore } from "../../stores/auth";
import { showSubmitNextApprovalDialog } from "../../utils/workflow-submit";

type ApprovalAction = "APPROVE" | "RETURN" | "SAVE" | "TERMINATE";

const NATIONAL_DEMAND_AGGREGATE_REASON = "需求池全国月度汇总生成";

interface ApprovalItemRow extends PurchaseRequestItem {
  rejected: boolean;
  rejectReason: string;
}

const loading = ref(false);
const submitLoading = ref(false);
const detailLoading = ref(false);
const approvalDetailLoading = ref(false);
const recordsLoading = ref(false);
const tableData = ref<PurchaseRequestVO[]>([]);
const total = ref(0);
const approvalVisible = ref(false);
const detailVisible = ref(false);
const recordsVisible = ref(false);
const flowStatusVisible = ref(false);
const approvalTarget = ref<PurchaseRequestVO | null>(null);
const approvalDetail = ref<PurchaseRequestVO | null>(null);
const currentDetail = ref<PurchaseRequestVO | null>(null);
const approvalRecords = ref<ApprovalRecordVO[]>([]);
const approvalDialogRecords = ref<ApprovalRecordVO[]>([]);
const detailRecords = ref<ApprovalRecordVO[]>([]);
const flowStatusRequest = ref<PurchaseRequestVO | null>(null);
const flowStatusRecords = ref<ApprovalRecordVO[]>([]);
const approvalItems = ref<ApprovalItemRow[]>([]);
const approvalFormRef = ref<FormInstance>();
const authStore = useAuthStore();

const queryModel = reactive({
  pageNo: 1,
  pageSize: 10,
  reqNo: "",
  reqTitle: ""
});

const approvalForm = reactive({
  action: "APPROVE" as ApprovalAction,
  targetNodeNo: null as number | null,
  approvalComment: ""
});

const smartHoldForm = reactive({
  text: "",
  summary: ""
});

const approvalRules: FormRules = {
  action: [{ required: true, message: "请选择审批动作", trigger: "change" }]
};

const canRejectItems = computed(() => Number(approvalTarget.value?.currentNodeNo || 0) >= 3);
const isOverPriceReview = computed(() => {
  const target = approvalDetail.value || approvalTarget.value;
  return Number(target?.currentNodeNo || 0) === 3
    && usesSupplierQuotePrice(target)
    && (Boolean(target?.overPriceReviewRequired)
      || Boolean(target?.items?.some((item) => item.overPriceReviewRequired)));
});
const canSmartHoldItems = computed(() => {
  const target = approvalDetail.value || approvalTarget.value;
  return Number(target?.currentNodeNo || 0) >= 4
    && target?.leaderConfirmStatus === "PENDING"
    && canRejectItems.value;
});
const canApproveCurrentDetail = computed(() => {
  return currentDetail.value?.status === "PENDING_APPROVAL" && currentDetail.value?.approvalStatus === "PENDING";
});
const canReturnApprovalTarget = computed(() =>
  !isOverPriceReview.value && !isSystemAggregateRequest(approvalDetail.value || approvalTarget.value)
);
const overPriceDeadlineText = computed(() => {
  const dueAt = (approvalDetail.value || approvalTarget.value)?.currentApprovalDueAt;
  if (!dueAt) {
    return "24小时内完成";
  }
  const deadline = new Date(dueAt).getTime();
  const remainingMinutes = Math.ceil((deadline - Date.now()) / 60000);
  if (remainingMinutes <= 0) {
    return `已超时 ${durationText(Math.abs(remainingMinutes))}；截止 ${formatDateTime(dueAt)}`;
  }
  return `剩余 ${durationText(remainingMinutes)}；截止 ${formatDateTime(dueAt)}`;
});
const flowStatusNodes = computed(() => flowNodesForRequest(flowStatusRequest.value, flowStatusRecords.value));
const previousNodeOptions = computed(() => {
  const currentNo = Number(approvalTarget.value?.currentNodeNo || 0);
  return flowNodesForRequest(approvalDetail.value, approvalDialogRecords.value)
    .filter((node) => node.nodeNo > 0 && node.nodeNo < currentNo);
});
const needsReturnTarget = computed(() => approvalForm.action === "RETURN" && previousNodeOptions.value.length > 0);
const canTerminateApprovalTarget = computed(() => {
  const target = approvalTarget.value;
  if (!target) {
    return false;
  }
  return target.applicantUserId === authStore.user?.userId
    && Number(target.currentNodeNo || 0) <= 0
    && target.status === "PENDING_APPROVAL"
    && target.approvalStatus === "PENDING";
});
const flowRecordRows = computed(() =>
  sortedRecords(flowStatusRecords.value).map((record, index) => ({
    index: index + 1,
    nodeName: record.nodeName || nodeName(record.nodeNo),
    createdAt: formatDateTime(record.createdAt),
    endedAt: formatDateTime(record.operatedAt),
    handler: record.approverName || (record.approverUserId ? `ID ${record.approverUserId}` : "-"),
    comment: record.approvalComment || "-",
    result: actionText(record.action)
  }))
);
const approvalDialogFlowRecordRows = computed(() =>
  sortedRecords(approvalDialogRecords.value).map((record, index) => ({
    index: index + 1,
    nodeName: record.nodeName || nodeName(record.nodeNo),
    createdAt: formatDateTime(record.createdAt),
    endedAt: formatDateTime(record.operatedAt),
    handler: record.approverName || (record.approverUserId ? `ID ${record.approverUserId}` : "-"),
    comment: record.approvalComment || "-",
    result: actionText(record.action)
  }))
);

async function loadTableData(): Promise<void> {
  loading.value = true;
  try {
    const data = await getApprovalTasksPageApi({
      pageNo: queryModel.pageNo,
      pageSize: queryModel.pageSize,
      reqNo: queryModel.reqNo || undefined,
      reqTitle: queryModel.reqTitle || undefined
    });
    tableData.value = data.records;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

function handleSearch(): void {
  queryModel.pageNo = 1;
  void loadTableData();
}

function resetQuery(): void {
  queryModel.reqNo = "";
  queryModel.reqTitle = "";
  queryModel.pageNo = 1;
  void loadTableData();
}

function handlePageChange(pageNo: number): void {
  queryModel.pageNo = pageNo;
  void loadTableData();
}

function handleSizeChange(pageSize: number): void {
  queryModel.pageSize = pageSize;
  queryModel.pageNo = 1;
  void loadTableData();
}

async function openApproval(row: PurchaseRequestVO): Promise<void> {
  approvalTarget.value = row;
  approvalDetail.value = null;
  approvalItems.value = [];
  approvalDialogRecords.value = [];
  approvalForm.action = "APPROVE";
  approvalForm.targetNodeNo = null;
  approvalForm.approvalComment = "";
  smartHoldForm.text = "";
  smartHoldForm.summary = "";
  approvalVisible.value = true;
  approvalDetailLoading.value = true;
  try {
    const [detail, records] = await Promise.all([getWorkflowDetailApi(row.id), getApprovalRecordsApi(row.id)]);
    approvalDetail.value = detail;
    approvalDialogRecords.value = records;
    approvalItems.value = (detail.items || []).map((item) => ({
      ...item,
      rejected: item.status === "REJECTED",
      rejectReason: item.status === "REJECTED" ? item.remark || "" : ""
    }));
  } finally {
    approvalDetailLoading.value = false;
  }
}

function applySmartHoldText(): void {
  const text = smartHoldForm.text.trim();
  if (!text) {
    ElMessage.warning("请先输入处理意见");
    return;
  }
  const activeItems = approvalItems.value.filter((item) => item.status === "ACTIVE");
  const matchedItems = activeItems.filter((item) => itemMatchesSmartText(item, text));
  if (matchedItems.length === 0) {
    ElMessage.warning("没有匹配到采购明细，请补充商品名称或在表格中手动选择");
    smartHoldForm.summary = "";
    return;
  }
  matchedItems.forEach((item) => {
    item.rejected = true;
    item.rejectReason = smartHoldReason(item, text);
  });
  activeItems
    .filter((item) => !matchedItems.includes(item))
    .forEach((item) => {
      if (!item.rejectReason) {
        item.rejected = false;
      }
    });
  const matchedText = rejectedItemSummary(matchedItems);
  const passedCount = Math.max(activeItems.length - matchedItems.length, 0);
  smartHoldForm.summary = `已标记 ${matchedItems.length} 项异常：${matchedText}；其余 ${passedCount} 项将按通过继续流转。`;
  if (!approvalForm.approvalComment.trim()) {
    approvalForm.approvalComment = `暂缓异常项：${matchedText}。处理意见：${text}`;
  }
}

function clearSmartHoldMarks(): void {
  approvalItems.value.forEach((item) => {
    if (item.status === "ACTIVE") {
      item.rejected = false;
      item.rejectReason = "";
    }
  });
  smartHoldForm.summary = "";
}

function itemMatchesSmartText(item: ApprovalItemRow, text: string): boolean {
  const normalizedText = normalizeSmartText(text);
  return smartMatchKeys(item).some((key) => key.length >= 2 && normalizedText.includes(key));
}

function smartMatchKeys(item: ApprovalItemRow): string[] {
  return [item.itemName, item.itemCode, item.specModel]
    .filter((value): value is string => Boolean(value && value.trim()))
    .map((value) => normalizeSmartText(value))
    .filter(Boolean);
}

function smartHoldReason(item: ApprovalItemRow, text: string): string {
  const clauses = text
    .split(/[，,。；;、\n]/)
    .map((part) => part.trim())
    .filter(Boolean);
  const matchedClause = clauses.find((clause) => itemMatchesSmartText(item, clause));
  const source = matchedClause || text;
  const itemName = item.itemName || item.itemCode || "该明细";
  if (/阻燃|防火|材质|证明|报告/.test(source)) {
    return `${itemName}需补充材质、防火或阻燃证明后再复审`;
  }
  if (/数量|够用|够不够|用量/.test(source)) {
    return `${itemName}需确认数量是否满足现场需求后再复审`;
  }
  if (/规格|型号|尺寸|厚度/.test(source)) {
    return `${itemName}需确认规格型号是否满足需求后再复审`;
  }
  return `${itemName}需补充说明后再复审：${source}`;
}

function normalizeSmartText(value: string): string {
  return value.replace(/\s+/g, "").toLowerCase();
}

function isSystemAggregateRequest(request?: PurchaseRequestVO | null): boolean {
  return request?.reqReason === NATIONAL_DEMAND_AGGREGATE_REASON;
}

async function submitApproval(actionOverride?: ApprovalAction): Promise<void> {
  if (!approvalTarget.value || !approvalFormRef.value) {
    return;
  }
  const action = actionOverride || approvalForm.action;
  const valid = await approvalFormRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }
  const approvalComment = approvalForm.approvalComment.trim();
  if (action === "TERMINATE" && !canTerminateApprovalTarget.value) {
    ElMessage.warning("只有发起人可以终止流程");
    return;
  }
  if (action === "RETURN" && !canReturnApprovalTarget.value) {
    ElMessage.warning("系统发起的全国月度汇总单不支持退回");
    approvalForm.action = "APPROVE";
    return;
  }
  if (["RETURN", "TERMINATE"].includes(action) && !approvalComment) {
    ElMessage.warning(action === "TERMINATE" ? "终止时请填写终止原因" : "退回时请填写审批意见");
    return;
  }
  if (action === "TERMINATE") {
    const confirmed = await ElMessageBox.confirm("确认终止当前采购审批流程？终止后该单不会继续流转。", "终止流程确认", {
      type: "warning",
      confirmButtonText: "终止",
      cancelButtonText: "取消"
    })
      .then(() => true)
      .catch(() => false);
    if (!confirmed) {
      return;
    }
  }

  const itemDecisions = approvalItems.value
    .filter((item) => item.rejected && item.status === "ACTIVE")
    .map((item) => ({
      itemId: item.id,
      rejected: true,
      rejectReason: item.rejectReason.trim()
    }));
  if (action === "APPROVE") {
    const missingReason = itemDecisions.some((item) => !item.rejectReason);
    if (missingReason) {
      ElMessage.warning("单项驳回时必须填写驳回原因");
      return;
    }
    if (itemDecisions.length > 0) {
      const confirmed = await confirmItemRejectSubmit();
      if (!confirmed) {
        return;
      }
    }
    if (isOverPriceReview.value) {
      const rejectedIds = new Set(itemDecisions.map((item) => item.itemId));
      const hasPassedOverPriceItem = approvalItems.value.some(
        (item) => item.overPriceReviewRequired && item.status === "ACTIVE" && !rejectedIds.has(item.id)
      );
      if (hasPassedOverPriceItem && !meaningfulOverPriceComment(approvalComment)) {
        ElMessage.warning("通过超价商品时必须填写具体的价格合理原因");
        return;
      }
    }
  }

  submitLoading.value = true;
  try {
    const result = await approveRequestApi(approvalTarget.value.id, {
      action,
      approvalComment: approvalComment || undefined,
      targetNodeNo: action === "RETURN" ? approvalForm.targetNodeNo : null,
      itemDecisions: action === "APPROVE" ? itemDecisions : undefined
    });
    ElMessage.success(action === "SAVE" ? "已暂存处理意见" : action === "TERMINATE" ? "流程已终止" : "审批提交成功");
    approvalVisible.value = false;
    if (action !== "SAVE" && action !== "TERMINATE") {
      await showSubmitNextApprovalDialog(result);
    }
    await loadTableData();
  } finally {
    submitLoading.value = false;
  }
}

async function openDetail(id: number): Promise<void> {
  detailVisible.value = true;
  detailLoading.value = true;
  currentDetail.value = null;
  detailRecords.value = [];
  try {
    const [detail, records] = await Promise.all([getWorkflowDetailApi(id), getApprovalRecordsApi(id)]);
    currentDetail.value = detail;
    detailRecords.value = records;
  } finally {
    detailLoading.value = false;
  }
}

async function openCurrentDetailApproval(): Promise<void> {
  if (!currentDetail.value) {
    return;
  }
  const target = currentDetail.value;
  detailVisible.value = false;
  await openApproval(target);
}

async function openRecords(row: PurchaseRequestVO): Promise<void> {
  recordsVisible.value = true;
  recordsLoading.value = true;
  approvalRecords.value = [];
  try {
    approvalRecords.value = await getApprovalRecordsApi(row.id);
  } finally {
    recordsLoading.value = false;
  }
}

function approvalStatusText(row: PurchaseRequestVO | string): string {
  if (typeof row !== "string" && isPartialApproved(row)) {
    return "部分通过";
  }
  const status = typeof row === "string" ? row : row.approvalStatus;
  const map: Record<string, string> = {
    DRAFT: "草稿",
    PENDING: "待审批",
    APPROVED: "通过",
    REJECTED: "驳回",
    RETURNED: "退回",
    TERMINATED: "已终止"
  };
  return map[status] || status || "-";
}

function isPartialApproved(row: PurchaseRequestVO): boolean {
  if (row.approvalStatus !== "APPROVED" || !row.items?.length) {
    return false;
  }
  const hasRejected = row.items.some((item) => item.status === "REJECTED");
  const hasActive = row.items.some((item) => item.status !== "REJECTED");
  return hasRejected && hasActive;
}

function approvalTagType(status: string): "warning" | "success" | "danger" | "info" {
  if (status === "PENDING") return "warning";
  if (status === "APPROVED") return "success";
  if (status === "REJECTED" || status === "TERMINATED") return "danger";
  return "info";
}

function applicantDisplay(row: PurchaseRequestVO): string {
  const name = row.applicantName || "-";
  return name !== "-" ? name : row.applicantEmployeeNo || "-";
}

function actingDelegationText(row: PurchaseRequestVO): string {
  const actingUser = row.actingUserName || (row.actingUserId ? String(row.actingUserId) : "管理员");
  const dept = row.deptName || row.deptCode || "-";
  const reason = row.actingReason ? `；原因：${row.actingReason}` : "";
  return `${actingUser} 代申请人选择部门 ${dept}${reason}`;
}

function itemStatusText(status?: string): string {
  if (status === "REJECTED") {
    return "已驳回";
  }
  if (status === "RETURNED") {
    return "退货";
  }
  return "有效";
}

function itemStatusTagType(status?: string): "success" | "warning" | "danger" | "info" {
  if (status === "REJECTED") {
    return "danger";
  }
  if (status === "RETURNED") {
    return "warning";
  }
  return "success";
}

function actionText(action: string): string {
  const map: Record<string, string> = {
    SUBMIT: "提交",
    APPROVE: "通过",
    REJECT: "驳回",
    RETURN: "退回",
    SAVE: "暂存",
    TERMINATE: "终止",
    APPROVE_REPLACEMENT: "使用替换商品报价",
    REJECT_REPLACEMENT: "使用原商品报价",
    CONFIRM_REPLACEMENT_BY_ITEM: "分项确认替换商品"
  };
  return map[action] || action || "-";
}

function nodeName(nodeNo: number | null | undefined): string {
  const map: Record<number, string> = {
    0: "提交申请",
    1: "值班长审核",
    2: "专业工程师审核",
    3: "楼长审核",
    4: "经理审核",
    5: "区域经理审核",
    6: "总监审核",
    7: "V7 审核",
    8: "V8 审核",
    10: "申请人替换商品确认"
  };
  return map[Number(nodeNo ?? 0)] || "审批节点";
}

function approvalNodeName(request?: PurchaseRequestVO | null): string {
  if (Number(request?.currentNodeNo || 0) === 3
    && usesSupplierQuotePrice(request)
    && (request?.overPriceReviewRequired || request?.items?.some((item) => item.overPriceReviewRequired))) {
    return "楼长超价复核";
  }
  return nodeName(request?.currentNodeNo);
}

function flowNodesForRequest(request: PurchaseRequestVO | null, records: ApprovalRecordVO[]): Array<{ nodeNo: number; name: string }> {
  if (!request) {
    return [];
  }
  const currentNodeNo = Number(request.currentNodeNo || 0);
  const included = new Set<number>([0]);
  records.forEach((record) => {
    const nodeNo = Number(record.nodeNo || 0);
    if (nodeNo >= 0) {
      included.add(nodeNo);
    }
  });
  if (isPendingApproval(request) && currentNodeNo > 0) {
    included.add(currentNodeNo);
  }
  return Array.from(included)
    .sort((left, right) => left - right)
    .map((nodeNo) => ({ nodeNo, name: nodeName(nodeNo) }));
}

function isPendingApproval(request: PurchaseRequestVO | null): boolean {
  return request?.status === "PENDING_APPROVAL" || request?.approvalStatus === "PENDING";
}

function sortedRecords(records: ApprovalRecordVO[]): ApprovalRecordVO[] {
  return [...records].sort((a, b) => String(a.operatedAt || a.createdAt || "").localeCompare(String(b.operatedAt || b.createdAt || "")));
}

function latestRecordForNode(records: ApprovalRecordVO[], nodeNo: number): ApprovalRecordVO | undefined {
  const matches = sortedRecords(records)
    .filter((record) => Number(record.nodeNo) === Number(nodeNo));
  return matches[matches.length - 1];
}

function flowNodeState(request: PurchaseRequestVO | null, records: ApprovalRecordVO[], nodeNo: number): string {
  const record = latestRecordForNode(records, nodeNo);
  if (isPendingApproval(request) && Number(request?.currentNodeNo) === Number(nodeNo)) {
    return "当前节点";
  }
  if (record?.action === "REJECT") {
    return "已驳回";
  }
  if (record?.action === "RETURN") {
    return "已退回";
  }
  if (record?.action === "TERMINATE") {
    return "已终止";
  }
  if (record?.action === "SAVE") {
    return "已暂存";
  }
  if (record?.action === "APPROVE") {
    return "已通过";
  }
  if (record?.action === "SUBMIT") {
    return "已提交";
  }
  if (request?.approvalStatus === "APPROVED" && Number(request.currentNodeNo) >= Number(nodeNo)) {
    return nodeNo === 0 ? "已提交" : "已通过";
  }
  return "未开始";
}

function flowNodeTagType(
  request: PurchaseRequestVO | null,
  records: ApprovalRecordVO[],
  nodeNo: number
): "success" | "warning" | "danger" | "info" {
  const state = flowNodeState(request, records, nodeNo);
  if (state === "已通过" || state === "已提交" || state === "已暂存") return "success";
  if (state === "当前节点") return "warning";
  if (state === "已驳回" || state === "已终止") return "danger";
  return "info";
}

function flowNodeClass(request: PurchaseRequestVO | null, records: ApprovalRecordVO[], nodeNo: number): string {
  const state = flowNodeState(request, records, nodeNo);
  if (state === "当前节点") return "is-current";
  if (state === "已通过" || state === "已提交" || state === "已暂存") return "is-done";
  if (state === "已驳回" || state === "已退回" || state === "已终止") return "is-stopped";
  return "";
}

function flowRecordMeta(record: ApprovalRecordVO | undefined): string {
  if (!record) {
    return "";
  }
  const user = record.approverName || record.approverUserId || "-";
  const role = record.approverRoleCode || "-";
  const time = formatDateTime(record.operatedAt || record.createdAt);
  return `审批人ID：${user} / 角色：${role} / 时间：${time}`;
}

function itemReasonText(row: ApprovalItemRow): string {
  if (row.status === "REJECTED" || row.rejected) {
    return row.rejectReason || row.remark || "-";
  }
  if (row.status === "RETURNED") {
    return row.remark || row.purpose || "-";
  }
  return row.purpose || row.remark || "-";
}

async function confirmItemRejectSubmit(): Promise<boolean> {
  const rejectedRows = approvalItems.value.filter((row) => row.rejected && row.status === "ACTIVE");
  const itemText = rejectedItemSummary(rejectedRows);
  const reqNo = approvalTarget.value?.reqNo || "当前申请";
  return ElMessageBox.confirm(
    `确认提交 ${reqNo} 的单项驳回商品信息？\n被驳回商品：${itemText}\n提交后，其余未驳回商品会按通过继续流转。`,
    "提交单项驳回商品信息",
    {
      type: "warning",
      confirmButtonText: "确认驳回商品",
      cancelButtonText: "取消"
    }
  )
    .then(() => true)
    .catch(() => false);
}

function rejectedItemDisplay(row: ApprovalItemRow): string {
  const name = row.itemName || row.itemCode || "商品明细";
  return row.lineNo ? `${row.lineNo}. ${name}` : name;
}

function rejectedItemSummary(rows: ApprovalItemRow[]): string {
  if (rows.length === 0) {
    return "已勾选商品";
  }
  const visibleItems = rows.slice(0, 3).map(rejectedItemDisplay);
  return rows.length > 3 ? `${visibleItems.join("、")} 等 ${rows.length} 项` : visibleItems.join("、");
}

function openFlowStatus(request: PurchaseRequestVO | null, records: ApprovalRecordVO[]): void {
  if (!request) {
    ElMessage.warning("暂无可查看的流程状态");
    return;
  }
  flowStatusRequest.value = request;
  flowStatusRecords.value = records;
  flowStatusVisible.value = true;
}

function formatDateTime(value?: string): string {
  if (!value) {
    return "-";
  }
  return value.replace("T", " ").slice(0, 19);
}

function durationText(totalMinutes: number): string {
  const normalized = Math.max(0, Math.floor(totalMinutes));
  return `${Math.floor(normalized / 60)}小时${normalized % 60}分`;
}

function meaningfulOverPriceComment(comment: string): boolean {
  const normalized = comment.replace(/[\s，。；、,.!！?？：:]+/g, "");
  return normalized.length >= 4 && !["同意", "通过", "确认", "价格合理", "同意通过"].includes(normalized);
}

function usesSupplierQuotePrice(request?: PurchaseRequestVO | null): boolean {
  return request?.priceSource === "SUPPLIER_QUOTE";
}

function totalAmountLabel(request?: PurchaseRequestVO | null): string {
  return usesSupplierQuotePrice(request) ? "供应商报价总额" : "参考总金额";
}

function lineAmountLabel(request?: PurchaseRequestVO | null): string {
  return usesSupplierQuotePrice(request) ? "供应商报价" : "参考金额";
}

function formatMoney(value?: number | null): string {
  return Number(value ?? 0).toLocaleString("zh-CN", {
    style: "currency",
    currency: "CNY",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function formatPercent(value?: number | null): string {
  return `${Number(value ?? 0).toLocaleString("zh-CN", { maximumFractionDigits: 2 })}%`;
}

function normalizeUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) {
    return url;
  }
  return `https://${url}`;
}

onMounted(() => {
  void loadTableData();
});
</script>

<style scoped>
.pager-box {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.dialog-summary {
  margin-bottom: 16px;
}

.detail-table {
  margin-top: 16px;
}

.approval-form {
  margin-top: 18px;
}

.approval-flow {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
  margin: 16px 0;
}

.smart-hold-box {
  display: grid;
  gap: 12px;
  margin: 16px 0 4px;
  padding: 14px;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fff7ed;
}

.smart-hold-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.smart-hold-head strong {
  display: block;
  color: #9a3412;
  font-size: 14px;
  line-height: 1.4;
}

.smart-hold-head span {
  display: block;
  margin-top: 4px;
  color: #7c2d12;
  font-size: 12px;
  line-height: 1.5;
}

.smart-hold-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: flex-start;
}

.smart-hold-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.smart-hold-result {
  padding: 9px 10px;
  border: 1px solid #fdba74;
  border-radius: 6px;
  background: #ffffff;
  color: #9a3412;
  font-size: 12px;
  line-height: 1.6;
}

.flow-node {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 8px;
  min-height: 74px;
  padding: 12px;
  border: 1px solid #d8e0ea;
  border-radius: 8px;
  background: #ffffff;
}

.flow-node.is-current {
  border-color: #f59e0b;
  background: #fffbeb;
}

.flow-node.is-done {
  border-color: #86efac;
  background: #f0fdf4;
}

.flow-node.is-stopped {
  border-color: #fecaca;
  background: #fef2f2;
}

.flow-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #eef2ff;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 700;
}

.flow-content {
  min-width: 0;
}

.flow-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.flow-meta {
  margin-top: 8px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.detail-records {
  margin-top: 18px;
}

.detail-records-title {
  margin-bottom: 10px;
  color: #101828;
  font-weight: 700;
}

.record-meta,
.record-comment {
  margin-top: 6px;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.flow-status-section + .flow-status-section {
  margin-top: 18px;
}

.flow-status-title {
  margin-bottom: 10px;
  color: #102a5c;
  font-size: 15px;
  font-weight: 700;
}

.inline-flow-status {
  margin-bottom: 18px;
}

.flow-map {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
  padding: 18px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.flow-map-node {
  position: relative;
  min-height: 118px;
  padding: 14px 14px 12px;
  border: 1px solid #d7e1f0;
  border-radius: 8px;
  background: #fff;
}

.flow-map-node.is-current {
  border-color: #f59e0b;
  background: #fffbeb;
}

.flow-map-node.is-done {
  border-color: #86efac;
  background: #f0fdf4;
}

.flow-map-node.is-stopped {
  border-color: #fca5a5;
  background: #fef2f2;
}

.flow-map-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-bottom: 10px;
  border-radius: 50%;
  background: #e8eefc;
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 700;
}

.flow-map-node strong,
.flow-map-node span,
.flow-map-node small {
  display: block;
}

.flow-map-node span {
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
}

.flow-map-node small {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.flow-map-arrow {
  position: absolute;
  top: 48px;
  right: -13px;
  z-index: 1;
  color: #94a3b8;
  font-style: normal;
  font-weight: 700;
}
</style>
