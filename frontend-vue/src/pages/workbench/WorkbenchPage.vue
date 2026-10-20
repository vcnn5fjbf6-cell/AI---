<template>
  <VnetPage title="工作台">
    <template #actions>
      <el-button :icon="Refresh" :loading="loading" @click="loadWorkbench">刷新</el-button>
    </template>

    <section class="metric-strip">
      <button
        v-for="metric in metrics"
        :key="metric.key"
        type="button"
        class="metric-tile"
        :class="[`metric-tile--${metric.tone}`, { 'is-active': activeFilter === metric.filter }]"
        @click="setFilter(metric.filter)"
      >
        <div class="metric-copy">
          <span>{{ metric.label }}</span>
          <small>{{ metric.note }}</small>
        </div>
        <strong>{{ metric.value }}</strong>
      </button>
    </section>


    <section v-if="hasOutsourcingAccess" class="outsourcing-workstream">
      <VnetPanel title="IT工程外包待办">
        <template #actions>
          <el-button link type="primary" @click="goOutsourcing('/engineering-outsourcing/workbench')">
            进入看板
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </template>
        <div class="outsourcing-todo-grid">
          <button
            v-for="item in outsourcingTodos"
            :key="item.title"
            type="button"
            class="outsourcing-todo-item"
            :class="`outsourcing-todo-item--${item.tone}`"
            @click="goOutsourcing(item.path)"
          >
            <span class="outsourcing-todo-icon"><el-icon><component :is="item.icon" /></el-icon></span>
            <span class="outsourcing-todo-copy">
              <strong>{{ item.title }}</strong>
              <small>{{ item.detail }}</small>
            </span>
            <b>{{ item.count }}</b>
            <el-icon class="outsourcing-todo-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
      </VnetPanel>
    </section>

    <div class="workbench-grid">
      <VnetPanel title="统一请求队列">
        <div class="queue-tabs">
          <button
            v-for="filter in queueFilters"
            :key="filter.value"
            type="button"
            :class="{ 'is-active': activeFilter === filter.value }"
            @click="setFilter(filter.value)"
          >
            {{ filter.label }}
            <span>{{ filter.count }}</span>
          </button>
        </div>

        <div class="queue-toolbar">
          <el-input
            v-model="query.keyword"
            class="queue-search"
            clearable
            :prefix-icon="Search"
            placeholder="搜索单号、标题、申请人"
          />
          <el-select v-model="query.statusText" clearable placeholder="全部状态" class="status-select">
            <el-option v-for="status in statusOptions" :key="status" :label="status" :value="status" />
          </el-select>
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD"
            class="date-range"
          />
          <el-button class="reset-query-button" @click="resetQuery">重置</el-button>
        </div>

        <el-table
          v-loading="loading"
          :data="pagedRows"
          stripe
          size="small"
          class="queue-table"
          :row-class-name="queueRowClassName"
        >
          <el-table-column label="" width="46" align="center">
            <template #default="{ row }">
              <el-tooltip :content="row.followed ? '取消关注' : '加入我的关注'" placement="top">
                <el-button
                  link
                  class="follow-button"
                  :class="{ 'is-followed': row.followed }"
                  :icon="Flag"
                  :loading="followLoadingId === row.id"
                  @click.stop="toggleFollow(row)"
                />
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="单号" width="140">
            <template #default="{ row }">
              <el-tooltip :content="row.reqNo" placement="top">
                <el-button link type="primary" class="request-link" @click="openRequest(row)">
                  {{ shortReqNo(row.reqNo) }}
                </el-button>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="260" show-overflow-tooltip />
          <el-table-column prop="applicant" label="发起人" width="110" />
          <el-table-column label="当前节点" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="node-cell">
                <span>{{ currentNodeDisplay(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="116">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" effect="light">{{ displayStatusText(row.statusText, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="arrivedAt" label="到达时间" min-width="150" />
        </el-table>

        <div class="pager-box">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next, jumper"
            :total="filteredRows.length"
            :current-page="page.pageNo"
            :page-size="page.pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </VnetPanel>
    </div>

    <section class="workbench-insight-grid">
      <VnetPanel title="本周处理">
        <div class="week-summary">
          <div v-for="item in weeklySummaryItems" :key="item.label">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <div class="mini-bars">
          <div v-for="item in weeklyModuleBars" :key="item.label" class="mini-bar-row">
            <span>{{ item.label }}</span>
            <div>
              <i :style="{ width: item.width }" />
            </div>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </VnetPanel>

      <VnetPanel title="审批进度">
        <div v-if="approvalProgressItems.length > 0" class="progress-list">
          <div v-for="item in approvalProgressItems" :key="item.label" class="progress-row">
            <span>{{ item.label }}</span>
            <el-progress :percentage="item.percent" :stroke-width="8" :show-text="false" />
            <strong>{{ item.value }}</strong>
          </div>
        </div>
        <el-empty v-else description="暂无流程数据" :image-size="62" />
      </VnetPanel>

      <VnetPanel title="公告栏">
        <template #actions>
          <el-button v-if="canManageWorkbenchNotices" link type="primary" :icon="Edit" @click="openNoticeEditor">
            编辑
          </el-button>
        </template>
        <div v-loading="noticeLoading" class="notice-board">
          <div v-if="visibleNotices.length > 0" class="notice-list">
            <article v-for="notice in visibleNotices" :key="notice.id" class="notice-item">
              <div>
                <strong>{{ notice.title }}</strong>
                <el-tag v-if="notice.pinned" size="small" type="primary" effect="plain">置顶</el-tag>
              </div>
              <p>{{ notice.content }}</p>
              <small>{{ formatDateTime(notice.updatedAt) }}</small>
            </article>
          </div>
          <el-empty v-else description="暂无公告" :image-size="62" />
        </div>
      </VnetPanel>
    </section>

    <el-dialog v-model="noticeEditorVisible" title="公告维护" width="780px" destroy-on-close>
      <div v-loading="noticeManageLoading" class="notice-editor">
        <el-form label-position="top" :model="noticeForm" class="notice-form">
          <div class="notice-form-grid">
            <el-form-item label="公告标题">
              <el-input v-model="noticeForm.title" maxlength="100" show-word-limit placeholder="请输入公告标题" />
            </el-form-item>
            <el-form-item label="排序">
              <el-input-number v-model="noticeForm.sortNo" :min="0" :max="9999" controls-position="right" />
            </el-form-item>
          </div>
          <el-form-item label="公告内容">
            <el-input
              v-model="noticeForm.content"
              type="textarea"
              :rows="4"
              maxlength="2000"
              show-word-limit
              placeholder="请输入公告内容"
            />
          </el-form-item>
          <div class="notice-form-actions">
            <el-switch v-model="noticeForm.pinned" active-text="置顶" />
            <el-switch v-model="noticeEnabled" active-text="启用" inactive-text="停用" />
            <span />
            <el-button @click="resetNoticeForm">新增</el-button>
            <el-button type="primary" :loading="noticeSaving" @click="saveNotice">保存公告</el-button>
          </div>
        </el-form>

        <el-table :data="managedNotices" size="small" stripe class="notice-manage-table">
          <el-table-column label="标题" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.title }}</span>
              <el-tag v-if="row.pinned" size="small" type="primary" effect="plain">置顶</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="82">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="light">
                {{ row.status === 1 ? "启用" : "停用" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sortNo" label="排序" width="76" />
          <el-table-column label="操作" width="140" align="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="editNotice(row)">编辑</el-button>
              <el-button link type="danger" @click="removeNotice(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      :title="dialogTitle"
      width="min(1180px, calc(100vw - 32px))"
      top="4vh"
      destroy-on-close
      class="workbench-dialog"
      :close-on-click-modal="false"
    >
      <div v-loading="detailLoading" class="detail-body">
        <el-descriptions v-if="selectedRow && !replacementConfirmDetail" :column="2" border class="detail-summary">
          <el-descriptions-item label="申请单号">{{ selectedRow.reqNo }}</el-descriptions-item>
          <el-descriptions-item label="当前节点">{{ currentNodeDisplay(selectedRow) }}</el-descriptions-item>
          <el-descriptions-item label="申请标题" :span="2">{{ selectedRow.title }}</el-descriptions-item>
          <el-descriptions-item label="发起人">{{ selectedRow.applicant }}</el-descriptions-item>
          <el-descriptions-item :label="workbenchAmountLabel">{{ selectedRow.amountText || "-" }}</el-descriptions-item>
          <el-descriptions-item v-if="usesSupplierQuotePrice" label="原参考金额">
            {{ selectedRow.referenceAmountText || "-" }}
          </el-descriptions-item>
        </el-descriptions>

        <dl v-if="selectedRow && replacementConfirmDetail" class="replacement-review-meta">
          <div><dt>申请单号</dt><dd>{{ selectedRow.reqNo }}</dd></div>
          <div><dt>原申请人</dt><dd>{{ selectedRow.applicant || "-" }}</dd></div>
          <div><dt>提交供应商</dt><dd>{{ replacementReviewSupplier }}</dd></div>
          <div><dt>提交时间</dt><dd>{{ formatDateTime(selectedRow.arrivedAt) || "-" }}</dd></div>
          <div><dt>SLA 剩余时间</dt><dd class="is-warning">{{ replacementReviewDeadline }}</dd></div>
        </dl>

        <section class="dialog-section">
          <div class="dialog-section-title">{{ replacementConfirmDetail ? "替换商品报价明细" : isSupplierQuoteDetail ? "报价明细" : "申请明细" }}</div>
          <div v-if="replacementConfirmDetail" class="replacement-compare-list">
            <article v-for="row in replacementReviewRows" :key="replacementRowKey(row)" class="replacement-compare-card">
              <div class="replacement-compare-head">
                <div>
                  <strong>{{ replacementCardTitle(row) }}</strong>
                  <span>{{ row.supplierName || "-" }} · {{ supplierContactText(row) }}</span>
                </div>
                <el-tag :type="statusTagType(row.status)" effect="plain">{{ itemStatusDisplayText(row) }}</el-tag>
              </div>
              <div class="replacement-comparison-stage">
                <div
                  class="replacement-option is-original"
                  :class="{
                    'is-selected': replacementSelection(row) === 'ORIGINAL',
                    'is-selectable': canSelectReplacement(row)
                  }"
                  :tabindex="canSelectReplacement(row) ? 0 : undefined"
                  role="button"
                  :aria-pressed="replacementSelection(row) === 'ORIGINAL'"
                  @click="selectReplacementOption(row, 'ORIGINAL')"
                  @keydown.enter.prevent="selectReplacementOption(row, 'ORIGINAL')"
                  @keydown.space.prevent="selectReplacementOption(row, 'ORIGINAL')"
                >
                  <div class="replacement-option-head">
                    <div class="replacement-option-title">
                      <strong>原商品报价</strong>
                      <span>按原商品继续</span>
                    </div>
                    <el-tag v-if="replacementSelection(row) === 'ORIGINAL'" size="small" effect="dark">已选择</el-tag>
                    <el-tag v-else-if="canSelectReplacement(row)" size="small" effect="plain">点击选择</el-tag>
                    <el-tag v-else size="small" effect="plain">按原商品继续</el-tag>
                  </div>
                  <div class="replacement-field-grid">
                    <span>商品</span><strong>{{ detailText(row.originalItemName || row.name) }}</strong>
                    <span>规格</span><strong>{{ detailText(row.originalSpec || row.spec) }}</strong>
                    <span>数量</span><strong>{{ quantityText(row) }}</strong>
                    <span>报价单价</span><strong>{{ moneyText(row.quoteUnitPrice) }}</strong>
                    <span>报价金额</span><strong>{{ row.quoteAmountText || moneyText(row.quoteAmount) }}</strong>
                    <span>交期</span><strong>{{ row.leadTimeDays != null ? `${row.leadTimeDays}天` : "-" }}</strong>
                  </div>
                </div>
                <div class="replacement-vs">VS</div>
                <div
                  class="replacement-option is-replacement"
                  :class="{
                    'is-selected': replacementSelection(row) === 'REPLACEMENT',
                    'is-selectable': canSelectReplacement(row)
                  }"
                  :tabindex="canSelectReplacement(row) ? 0 : undefined"
                  role="button"
                  :aria-pressed="replacementSelection(row) === 'REPLACEMENT'"
                  @click="selectReplacementOption(row, 'REPLACEMENT')"
                  @keydown.enter.prevent="selectReplacementOption(row, 'REPLACEMENT')"
                  @keydown.space.prevent="selectReplacementOption(row, 'REPLACEMENT')"
                >
                  <div class="replacement-option-head">
                    <div class="replacement-option-title">
                      <strong>替换商品报价</strong>
                      <span>供应商建议替换</span>
                    </div>
                    <el-tag v-if="replacementSelection(row) === 'REPLACEMENT'" size="small" type="success" effect="dark">已选择</el-tag>
                    <el-tag v-else-if="canSelectReplacement(row)" size="small" type="success" effect="plain">点击选择</el-tag>
                    <el-tag v-else size="small" type="success" effect="plain">供应商建议替换</el-tag>
                  </div>
                  <div class="replacement-field-grid">
                    <span>商品</span><strong>{{ detailText(row.replacementItemName) }}</strong>
                    <span>请求供应商</span><strong>{{ replacementSupplierText(row) }}</strong>
                    <span>规格</span><strong :class="{ 'is-different': replacementSpecChanged(row) }">{{ detailText(row.replacementSpec) }}</strong>
                    <span>替换单价</span><strong :class="{ 'is-different': replacementPriceDifference(row) !== 0 }">{{ moneyText(row.replacementUnitPrice) }}</strong>
                    <span>替换金额</span><strong>{{ moneyText(row.replacementAmount) }}</strong>
                    <span>替换原因</span><strong>{{ detailText(row.replacementReason) }}</strong>
                    <span>商品链接</span>
                    <strong>
                      <el-link
                        v-if="hasText(row.replacementEcommerceUrl)"
                        :href="normalizeOptionalUrl(row.replacementEcommerceUrl)"
                        target="_blank"
                        type="primary"
                      >
                        打开链接
                      </el-link>
                      <span v-else>-</span>
                    </strong>
                  </div>
                </div>
              </div>
              <div v-if="replacementHasDifference(row)" class="replacement-difference-banner">
                <strong>重要差异</strong>
                <span>{{ replacementDifferenceText(row) }}</span>
              </div>
              <div v-if="hasText(row.remark)" class="replacement-quote-remark">
                报价说明：{{ detailText(row.remark) }}
              </div>
            </article>
          </div>
          <el-table v-else :data="displayDetailRows" stripe class="detail-table" max-height="260">
            <el-table-column prop="lineNo" label="行号" width="70" />
            <el-table-column v-if="replacementConfirmDetail" prop="originalItemName" label="原商品" min-width="160" show-overflow-tooltip />
            <el-table-column v-if="replacementConfirmDetail" prop="replacementItemName" label="替换商品" min-width="170" show-overflow-tooltip />
            <el-table-column v-if="replacementConfirmDetail" prop="replacementSpec" label="替换规格" min-width="140" show-overflow-tooltip />
            <el-table-column v-if="replacementConfirmDetail" prop="supplierName" label="报价供应商" min-width="160" show-overflow-tooltip />
            <el-table-column v-if="replacementConfirmDetail" prop="supplierContactPhone" label="联系电话" min-width="130" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ supplierContactText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="replacementConfirmDetail" prop="quoteUnitPrice" label="报价单价" width="110">
              <template #default="{ row }">{{ moneyText(row.quoteUnitPrice) }}</template>
            </el-table-column>
            <el-table-column v-if="replacementConfirmDetail" prop="quoteAmountText" label="报价金额" width="120" />
            <el-table-column v-if="!replacementConfirmDetail" prop="name" label="产品名称" min-width="180" show-overflow-tooltip />
            <el-table-column v-if="!replacementConfirmDetail && !isSupplierQuoteDetail" prop="roomName" label="机房/部门" min-width="220" show-overflow-tooltip />
            <el-table-column v-if="isOverPriceReviewDetail" label="提交者 / 来源工单" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.sourceApplicantName || selectedRow?.applicant || "-" }}
                <span v-if="row.sourceRequestNo"> / {{ row.sourceRequestNo }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!replacementConfirmDetail" prop="spec" label="规格/范围" min-width="150" show-overflow-tooltip />
            <el-table-column prop="qty" label="数量" width="80" />
            <el-table-column prop="unit" label="单位" width="80" />
            <el-table-column
              v-if="!replacementConfirmDetail && !isSupplierQuoteDetail && usesSupplierQuotePrice"
              prop="supplierName"
              label="中标供应商"
              min-width="160"
              show-overflow-tooltip
            >
              <template #default="{ row }">{{ row.supplierName || "-" }}</template>
            </el-table-column>
            <el-table-column v-if="!replacementConfirmDetail && !isSupplierQuoteDetail" prop="amountText" :label="workbenchLineAmountLabel" width="130" />
            <el-table-column
              v-if="!replacementConfirmDetail && !isSupplierQuoteDetail && usesSupplierQuotePrice"
              prop="referenceAmountText"
              label="原参考金额"
              width="130"
            />
            <el-table-column v-if="isOverPriceReviewDetail" label="超出金额 / 比例" width="155">
              <template #default="{ row }">
                <strong v-if="row.overPriceReviewRequired" class="over-price-value">
                  +{{ moneyText(row.overPriceLineDifference) }} / +{{ formatPercent(row.overPriceRatePercent) }}
                </strong>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="statusText" :label="replacementConfirmDetail ? '中标状态' : '状态'" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" effect="plain">{{ itemStatusDisplayText(row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="replacementConfirmDetail" prop="leadTimeDays" label="交期" width="90">
              <template #default="{ row }">{{ row.leadTimeDays != null ? `${row.leadTimeDays}天` : "-" }}</template>
            </el-table-column>
            <el-table-column v-if="canRejectItemsInWorkbench" label="单项驳回" width="110">
              <template #default="{ row }">
                <el-switch
                  v-model="row.rejected"
                  :disabled="row.status !== 'ACTIVE' || approvalForm.action !== 'APPROVE' || (isOverPriceReviewDetail && !row.overPriceReviewRequired)"
                  @change="handleItemRejectChange(row)"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="showPurchaseReasonColumn" :label="replacementConfirmDetail ? '报价说明' : '采购原因/驳回原因'" min-width="240">
              <template #default="{ row }">
                <el-input
                  v-if="!replacementConfirmDetail && row.rejected && row.status === 'ACTIVE' && canRejectItemsInWorkbench"
                  v-model="row.rejectReasonInput"
                  clearable
                  placeholder="填写单项驳回原因"
                />
                <span v-else>{{ itemReasonText(row) }}</span>
              </template>
            </el-table-column>
            <el-table-column v-else prop="remark" label="说明" min-width="200" show-overflow-tooltip />
          </el-table>
        </section>

        <section v-if="isAfterSaleApprovalDetail" class="dialog-section after-sale-plan-section">
          <div class="dialog-section-title">售后方案</div>
          <el-alert
            v-if="isAfterSaleSupplierProposal && !showAfterSaleProposalSummary"
            type="info"
            :closable="false"
            show-icon
            title="请供应商给出退换、维修、补发或更换商品方案。更换商品时，方案单价最多只能比当前单价高 400 元。"
          />
          <el-descriptions v-else :column="2" border size="small" class="after-sale-plan-descriptions">
            <el-descriptions-item label="供应商方案" :span="2">
              {{ detailText(afterSaleDetailRow?.supplierProposalRemark) }}
            </el-descriptions-item>
            <el-descriptions-item label="方案商品">
              {{ detailText(afterSaleDetailRow?.supplierReplacementItemName) }}
            </el-descriptions-item>
            <el-descriptions-item label="方案规格">
              {{ detailText(afterSaleDetailRow?.supplierReplacementSpecModel) }}
            </el-descriptions-item>
            <el-descriptions-item label="方案单价">
              {{ moneyText(afterSaleDetailRow?.supplierReplacementUnitPrice) }}
            </el-descriptions-item>
            <el-descriptions-item label="方案金额">
              {{ moneyText(afterSaleDetailRow?.supplierReplacementLineAmount) }}
            </el-descriptions-item>
            <el-descriptions-item label="价格上限">
              {{ afterSalePriceLimitText || "-" }}
            </el-descriptions-item>
            <el-descriptions-item label="给出时间">
              {{ formatDateTime(afterSaleDetailRow?.supplierProposedAt || "") || "-" }}
            </el-descriptions-item>
            <el-descriptions-item v-if="hasText(afterSaleDetailRow?.requesterConfirmRemark)" label="需求方意见" :span="2">
              {{ detailText(afterSaleDetailRow?.requesterConfirmRemark) }}
            </el-descriptions-item>
          </el-descriptions>
        </section>

        <section v-if="isSupplierQuoteDetail" class="dialog-section quote-guide-section">
          <div class="dialog-section-title">报价入口</div>
          <div class="quote-guide-card">
            <div>
              <strong>{{ supplierQuoteGuideTitle }}</strong>
              <p>{{ supplierQuoteGuideText }}</p>
            </div>
            <el-button type="primary" @click="goSupplierQuotePage">{{ supplierQuoteGuideButton }}</el-button>
          </div>
        </section>

        <section v-else class="dialog-section action-section">
          <div class="dialog-section-title">{{ replacementConfirmDetail ? "确认说明" : "审批动作" }}</div>
          <el-alert
            v-if="!selectedRow?.actionable && !canTerminateSelected"
            type="info"
            :closable="false"
            show-icon
            title="该请求当前只提供查看，审批提交接口尚未接入统一工作台。"
          />
          <el-form v-else label-position="top" :model="approvalForm" class="approval-action-form">
            <el-alert
              v-if="replacementConfirmDetail"
              type="info"
              :closable="false"
              show-icon
              title="每项选择原商品或替换商品后提交。"
              class="approval-deadline-alert"
            />
            <el-alert
              v-if="approvalDeadlineTip"
              type="warning"
              :closable="false"
              show-icon
              :title="approvalDeadlineTip"
              class="approval-deadline-alert"
            />
            <el-form-item v-if="selectedRow?.actionable && !replacementConfirmDetail" :label="approvalActionLabel">
              <el-radio-group v-model="approvalForm.action">
                <el-radio-button
                  v-for="option in approvalActionOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>

            <template v-if="showAfterSaleProposalForm">
              <el-alert
                type="warning"
                :closable="false"
                show-icon
                :title="afterSalePriceLimitText"
                description="供应商可以提出更换商品方案；如果方案单价高于现价，最多只能高 400 元，超出会被系统拒绝。"
                class="approval-deadline-alert"
              />
              <div class="action-grid after-sale-plan-grid">
                <el-form-item label="方案类型">
                  <el-select v-model="afterSaleProposalForm.proposalType">
                    <el-option label="无理由退货" value="NO_REASON_RETURN" />
                    <el-option label="退换货/补发" value="RETURN_EXCHANGE" />
                    <el-option label="更换商品" value="REPLACE_ITEM" />
                    <el-option label="维修处理" value="REPAIR" />
                    <el-option label="其他方案" value="OTHER" />
                  </el-select>
                </el-form-item>
                <el-form-item label="方案商品">
                  <el-input v-model="afterSaleProposalForm.replacementItemName" clearable placeholder="不更换商品可留空" />
                </el-form-item>
                <el-form-item label="方案规格">
                  <el-input v-model="afterSaleProposalForm.replacementSpecModel" clearable placeholder="可填写品牌、型号、材质等" />
                </el-form-item>
                <el-form-item label="商品编码">
                  <el-input v-model="afterSaleProposalForm.replacementItemCode" clearable placeholder="可选" />
                </el-form-item>
                <el-form-item label="单位">
                  <el-input v-model="afterSaleProposalForm.replacementUnit" clearable placeholder="默认沿用原订单单位" />
                </el-form-item>
                <el-form-item label="方案单价">
                  <el-input-number
                    v-model="afterSaleProposalForm.replacementUnitPrice"
                    :min="0"
                    :precision="2"
                    :step="1"
                    controls-position="right"
                    @change="syncAfterSaleProposalAmount"
                  />
                </el-form-item>
                <el-form-item label="方案金额">
                  <el-input-number
                    v-model="afterSaleProposalForm.replacementLineAmount"
                    :min="0"
                    :precision="2"
                    :step="1"
                    controls-position="right"
                  />
                </el-form-item>
              </div>
            </template>

            <el-alert
              v-if="isAfterSaleProposalConfirmation && selectedRow?.actionable"
              type="info"
              :closable="false"
              show-icon
              title="同意后售后方案生效；不同意会退回供应商重新给方案。"
              class="approval-deadline-alert"
            />

            <div class="action-grid">
              <el-form-item v-if="needsReturnTarget" label="退回到节点">
                <el-select v-model="approvalForm.targetNodeNo" clearable placeholder="不选则退回申请人补充">
                  <el-option
                    v-for="node in previousNodeOptions"
                    :key="node.nodeNo"
                    :label="returnTargetLabel(node)"
                    :value="node.nodeNo"
                  />
                </el-select>
              </el-form-item>
              <el-form-item v-if="selectedRow?.actionable && approvalForm.action === 'ADD_SIGN'" label="加签人">
                <el-select v-model="approvalForm.addSignUserId" filterable clearable placeholder="选择人员，显示姓名">
                  <el-option
                    v-for="user in userOptions"
                    :key="user.id"
                    :label="user.displayName"
                    :value="user.id"
                  />
                </el-select>
              </el-form-item>
            </div>

            <div v-if="isPurchaseApprovalDetail" class="inline-flow-status">
              <div class="dialog-section-title">流程状态</div>
              <div class="inline-flow-map">
                <div v-for="node in detailFlowNodes" :key="node.nodeNo" class="inline-flow-node" :class="`is-${node.state}`">
                  <span>{{ node.nodeNo }}</span>
                  <strong>{{ node.name }}</strong>
                  <small>{{ node.stateText || flowStateText(node.state) }}</small>
                </div>
              </div>
              <el-table :data="flowRecordRows" border size="small" max-height="180" class="inline-flow-records">
                <el-table-column prop="nodeName" label="节点名称" min-width="140" />
                <el-table-column prop="endedAt" label="结束时间" min-width="150" />
                <el-table-column prop="handler" label="处理人" min-width="140" />
                <el-table-column prop="comment" label="处理意见" min-width="220" show-overflow-tooltip />
                <el-table-column prop="result" label="处理结果" width="110" />
              </el-table>
            </div>

            <el-form-item :label="approvalCommentLabel">
              <el-input
                v-model="approvalForm.comment"
                type="textarea"
                :rows="4"
                maxlength="500"
                show-word-limit
                :placeholder="approvalCommentPlaceholder"
              />
            </el-form-item>
          </el-form>
        </section>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">取消</el-button>
        <el-button v-if="!isSupplierQuoteDetail" @click="flowStatusVisible = true">
          流程状态
        </el-button>
        <el-button v-if="isSupplierQuoteDetail" type="primary" @click="goSupplierQuotePage">
          去上传报价
        </el-button>
        <el-button
          v-if="selectedRow?.actionable && isPurchaseApprovalDetail && !replacementConfirmDetail"
          :loading="submitLoading"
          @click="submitApprovalAction('SAVE')"
        >
          暂存
        </el-button>
        <el-button
          v-if="canTerminateSelected"
          type="danger"
          plain
          :loading="submitLoading"
          @click="submitApprovalAction('TERMINATE')"
        >
          终止
        </el-button>
        <el-button v-if="selectedRow?.actionable" type="primary" :loading="submitLoading" @click="submitApprovalAction()">
          {{ submitButtonText }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="flowStatusVisible" title="流程状态" width="1180px" top="4vh" class="flow-status-dialog">
      <section class="flow-status-section">
        <div class="dialog-section-title">流程记录</div>
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
        <div class="dialog-section-title">流程图</div>
        <div class="flow-map">
          <div v-for="(node, index) in detailFlowNodes" :key="node.nodeNo" class="flow-map-node" :class="`is-${node.state}`">
            <div class="flow-index">{{ node.nodeNo }}</div>
            <strong>{{ node.name }}</strong>
            <span>{{ node.stateText || flowStateText(node.state) }}</span>
            <small>{{ node.meta || "等待流程推进" }}</small>
            <i v-if="index < detailFlowNodes.length - 1" class="flow-map-arrow">→</i>
          </div>
        </div>
      </section>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { ArrowRight, Calendar, CircleCheck, Document, Edit, Flag, Refresh, Search, Wallet } from "@element-plus/icons-vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import { useAuthStore } from "../../stores/auth";
import {
  approveWorkbenchRequestApi,
  createWorkbenchNoticeApi,
  deleteWorkbenchNoticeApi,
  getWorkbenchManageNoticesApi,
  getWorkbenchNoticesApi,
  getWorkbenchRequestDetailApi,
  getWorkbenchRequestsApi,
  getWorkbenchUserOptionsApi,
  setWorkbenchFollowApi,
  updateWorkbenchNoticeApi
} from "../../api/workbench";
import type {
  WorkbenchAction,
  WorkbenchDetailRowVO,
  WorkbenchFlowNodeVO,
  WorkbenchMetricVO,
  WorkbenchModule,
  WorkbenchNoticeForm,
  WorkbenchNoticeVO,
  WorkbenchRequestDetailVO,
  WorkbenchRequestVO,
  WorkbenchUserOptionVO
} from "../../types/workbench";
import { showSubmitNextApprovalDialog } from "../../utils/workflow-submit";

type QueueFilter =
  | "all"
  | "todo"
  | "mine"
  | "related"
  | "department"
  | "done"
  | "followed"
  | `module:${WorkbenchModule}`;
type TagType = "success" | "warning" | "danger" | "info" | "primary" | "";
type ReplacementSelection = "REPLACEMENT" | "ORIGINAL";

interface WorkbenchApprovalItemRow extends WorkbenchDetailRowVO {
  rejected: boolean;
  rejectReasonInput: string;
}

const loading = ref(false);
const router = useRouter();
const authStore = useAuthStore();
const detailVisible = ref(false);
const flowStatusVisible = ref(false);
const detailLoading = ref(false);
const submitLoading = ref(false);
const followLoadingId = ref("");
const noticeLoading = ref(false);
const noticeManageLoading = ref(false);
const noticeSaving = ref(false);
const noticeEditorVisible = ref(false);
const rows = ref<WorkbenchRequestVO[]>([]);
const selectedRow = ref<WorkbenchRequestVO | null>(null);
const detailData = ref<WorkbenchRequestDetailVO | null>(null);
const approvalItemRows = ref<WorkbenchApprovalItemRow[]>([]);
const replacementSelections = reactive<Record<string, ReplacementSelection>>({});
const backendMetrics = ref<WorkbenchMetricVO[]>([]);
const userOptions = ref<WorkbenchUserOptionVO[]>([]);
const visibleNotices = ref<WorkbenchNoticeVO[]>([]);
const managedNotices = ref<WorkbenchNoticeVO[]>([]);
const activeFilter = ref<QueueFilter>("todo");
const editingNoticeId = ref<number | null>(null);

const query = reactive({
  keyword: "",
  statusText: "",
  dateRange: [] as string[]
});

const page = reactive({
  pageNo: 1,
  pageSize: 10
});

const approvalForm = reactive({
  action: "APPROVE" as WorkbenchAction,
  targetNodeNo: null as number | null,
  addSignUserId: null as number | null,
  comment: ""
});

const afterSaleProposalForm = reactive({
  proposalType: "REPLACE_ITEM",
  replacementItemCode: "",
  replacementItemName: "",
  replacementSpecModel: "",
  replacementUnit: "",
  replacementUnitPrice: null as number | null,
  replacementLineAmount: null as number | null
});

const noticeForm = reactive<WorkbenchNoticeForm>({
  title: "",
  content: "",
  pinned: false,
  status: 1,
  sortNo: 100
});

const metrics = computed(() => [
  metricTile("todo", "待办", "需要进入弹窗处理", "primary", "todo"),
  metricTile("done", "已办", "近期完成或归档", "neutral", "done"),
  metricTile("related", "抄送", "抄送给我的流程", "warning", "related"),
  metricTile("mine", "我发起", "跟踪我提交的申请", "success", "mine"),
  metricTile("department", "部门工单", "助理可查看的本部门申请", "neutral", "department")
]);

const businessUnitFallbackNames: Record<WorkbenchModule, string> = {
  PROCUREMENT: "采购管理",
  MAINTENANCE: "维保管理",
  PLATFORM: "平台管理",
  SUPPLIER: "供应商管理"
};
const queueFilters = computed(() => [
  { label: "待办", value: "todo" as QueueFilter, count: countRows((row) => row.relation === "todo") },
  { label: "已办", value: "done" as QueueFilter, count: countRows((row) => row.relation === "done") },
  { label: "抄送", value: "related" as QueueFilter, count: countRows((row) => row.relation === "related") }
]);

const statusOptions = computed(() => Array.from(new Set(rows.value.map((row) => row.statusText).filter(Boolean))));

const filteredRows = computed(() => {
  const keyword = query.keyword.trim().toLowerCase();
  const [startDate, endDate] = query.dateRange || [];
  return rows.value.filter((row) => {
    if (!matchesFilter(row, activeFilter.value)) {
      return false;
    }
    if (query.statusText && row.statusText !== query.statusText) {
      return false;
    }
    if (keyword && !`${row.reqNo} ${row.title} ${row.applicant}`.toLowerCase().includes(keyword)) {
      return false;
    }
    const rowDate = row.arrivedAt ? row.arrivedAt.slice(0, 10) : "";
    if (startDate && rowDate && rowDate < startDate) {
      return false;
    }
    if (endDate && rowDate && rowDate > endDate) {
      return false;
    }
    return true;
  }).sort(compareArrivedAtDesc);
});

const pagedRows = computed(() => {
  const start = (page.pageNo - 1) * page.pageSize;
  return filteredRows.value.slice(start, start + page.pageSize);
});

const detailFlowNodes = computed<WorkbenchFlowNodeVO[]>(() => detailData.value?.flowNodes ?? []);
const detailRows = computed<WorkbenchDetailRowVO[]>(() => detailData.value?.detailRows ?? []);
const displayDetailRows = computed(() => (isPurchaseApprovalDetail.value ? approvalItemRows.value : detailRows.value));
const flowRecordRows = computed(() =>
  [...(detailData.value?.approvalRecords ?? [])]
    .sort((left, right) =>
      String(left.operatedAt || left.createdAt || "").localeCompare(String(right.operatedAt || right.createdAt || ""))
    )
    .map((record, index) => ({
      index: index + 1,
      nodeName: record.nodeName || `节点 ${record.nodeNo}`,
      createdAt: formatDateTime(record.createdAt),
      endedAt: formatDateTime(record.operatedAt),
      handler: record.approverName || (record.approverUserId ? `ID ${record.approverUserId}` : "-"),
      comment: record.approvalComment || "-",
      result: actionText(record.action)
    }))
);
const canManageWorkbenchNotices = computed(() => authStore.hasAnyPermission(["workbench_notice:manage"]));
const noticeEnabled = computed({
  get: () => noticeForm.status === 1,
  set: (value: boolean) => {
    noticeForm.status = value ? 1 : 0;
  }
});
const weekRows = computed(() => {
  const start = weekStart(new Date());
  return rows.value.filter((row) => {
    const time = parseLocalDateTime(row.arrivedAt);
    return time != null && time >= start;
  });
});
const weeklySummaryItems = computed(() => [
  { label: "待办", value: countRows((row) => row.relation === "todo") },
  { label: "本周已办", value: weekRows.value.filter((row) => row.relation === "done").length },
  { label: "本周发起", value: weekRows.value.filter((row) => row.relation === "mine").length },
  { label: "部门工单", value: countRows((row) => row.relation === "department") }
]);
const weeklyModuleBars = computed(() => {
  const moduleCounts = new Map<string, number>();
  weekRows.value.forEach((row) => {
    const label = businessUnitName(row);
    moduleCounts.set(label, (moduleCounts.get(label) || 0) + 1);
  });
  const max = Math.max(1, ...moduleCounts.values());
  return Array.from(moduleCounts.entries())
    .sort((left, right) => right[1] - left[1])
    .slice(0, 5)
    .map(([label, value]) => ({ label, value, width: `${Math.max(8, Math.round((value / max) * 100))}%` }));
});
const approvalProgressItems = computed(() => {
  const statusCounts = new Map<string, number>();
  rows.value.forEach((row) => {
    statusCounts.set(row.statusText || "-", (statusCounts.get(row.statusText || "-") || 0) + 1);
  });
  const total = Math.max(1, rows.value.length);
  return Array.from(statusCounts.entries())
    .sort((left, right) => right[1] - left[1])
    .slice(0, 5)
    .map(([label, value]) => ({
      label,
      value,
      percent: Math.round((value / total) * 100)
    }));
});
const isSupplierAccount = computed(() => {
  const userType = authStore.user?.userType?.trim().toUpperCase();
  return userType === "SUPPLIER" || authStore.user?.supplierId != null;
});
const hasOutsourcingAccess = computed(() => authStore.hasApp("OUTSOURCING"));

const outsourcingTodos = computed(() => isSupplierAccount.value
  ? [
      { title: "待确认接单", count: 2, detail: "2 单等待服务商确认", path: "/engineering-outsourcing/orders", icon: Document, tone: "primary" },
      { title: "入场预约", count: 1, detail: "明日 09:00 计划到场", path: "/engineering-outsourcing/entry", icon: Calendar, tone: "warning" },
      { title: "工作量待填报", count: 2, detail: "实施中工单等待填报", path: "/engineering-outsourcing/workload", icon: CircleCheck, tone: "success" },
      { title: "账单待确认", count: 1, detail: "争议金额 ¥3,200", path: "/engineering-outsourcing/settlement", icon: Wallet, tone: "danger" }
    ]
  : [
      { title: "工单待审批", count: 3, detail: "其中 1 单将在 4 小时后超时", path: "/engineering-outsourcing/orders", icon: Document, tone: "primary" },
      { title: "入场预约待确认", count: 2, detail: "最早计划到场：明日 09:00", path: "/engineering-outsourcing/entry", icon: Calendar, tone: "warning" },
      { title: "工作量待验收", count: 4, detail: "含 1 单整改后复验", path: "/engineering-outsourcing/acceptance", icon: CircleCheck, tone: "success" },
      { title: "账单待处理", count: 5, detail: "云产线 2 单，数据中心 3 单", path: "/engineering-outsourcing/settlement", icon: Wallet, tone: "danger" }
    ]);
const isSupplierQuoteDetail = computed(() => isSupplierAccount.value && selectedRow.value?.sourceType === "SUPPLIER");
const isPurchaseApprovalDetail = computed(() => selectedRow.value?.sourceType === "PURCHASE");
const isOverPriceReviewDetail = computed(
  () =>
    isPurchaseApprovalDetail.value
    && Number(selectedRow.value?.currentNodeNo || 0) === 3
    && detailRows.value.some((row) => Boolean(row.overPriceReviewRequired))
);
const usesSupplierQuotePrice = computed(
  () => isPurchaseApprovalDetail.value && selectedRow.value?.priceSource === "SUPPLIER_QUOTE"
);
const workbenchAmountLabel = computed(() => (usesSupplierQuotePrice.value ? "供应商报价总额" : "参考总金额"));
const workbenchLineAmountLabel = computed(() => (usesSupplierQuotePrice.value ? "供应商报价" : "参考金额"));
const replacementConfirmDetail = computed(
  () =>
    isPurchaseApprovalDetail.value
    && Number(selectedRow.value?.currentNodeNo || 0) === 10
);
const replacementReviewRows = computed(() => displayDetailRows.value.filter(isAwardedReplacementRow));
const replacementReviewSupplier = computed(() => replacementReviewRows.value[0]?.supplierName || "-");
const replacementReviewDeadline = computed(() => remainingDeadlineText(detailData.value?.currentApprovalDueAt));
const isAfterSaleApprovalDetail = computed(() => selectedRow.value?.sourceType === "AFTER_SALE");
const afterSaleDetailRow = computed(() => (isAfterSaleApprovalDetail.value ? detailRows.value[0] : null));
const isAfterSaleSupplierProposal = computed(
  () => isAfterSaleApprovalDetail.value && selectedRow.value?.status === "PENDING"
);
const isAfterSaleProposalConfirmation = computed(
  () => isAfterSaleApprovalDetail.value && selectedRow.value?.status === "PROPOSAL_PENDING"
);
const showAfterSaleProposalForm = computed(
  () => isAfterSaleSupplierProposal.value && Boolean(selectedRow.value?.actionable) && approvalForm.action === "APPROVE"
);
const showAfterSaleProposalSummary = computed(
  () => isAfterSaleApprovalDetail.value && Boolean(afterSaleDetailRow.value?.supplierProposalRemark || afterSaleDetailRow.value?.supplierReplacementItemName)
);
const afterSalePriceLimitText = computed(() => {
  const row = afterSaleDetailRow.value;
  if (!row) {
    return "";
  }
  const original = moneyText(row.originalUnitPrice);
  const limit = moneyText(row.priceIncreaseLimit);
  return `当前单价 ${original}，方案单价最高 ${limit}。`;
});
const canTerminateSelected = computed(() => isPurchaseApprovalDetail.value && Boolean(selectedRow.value?.terminable));
const canReturnSelected = computed(() => selectedRow.value?.returnable !== false && !isOverPriceReviewDetail.value);
const canRejectItemsInWorkbench = computed(
  () =>
    isPurchaseApprovalDetail.value
    && !replacementConfirmDetail.value
    && Boolean(selectedRow.value?.actionable)
    && approvalForm.action === "APPROVE"
    && Number(selectedRow.value?.currentNodeNo || 0) >= 3
);
const approvalDeadlineTip = computed(() => {
  const dueAt = detailData.value?.currentApprovalDueAt;
  if (!isPurchaseApprovalDetail.value || !selectedRow.value?.actionable || !dueAt) {
    return "";
  }
  if (replacementConfirmDetail.value) {
    return `截止 ${formatDateTime(dueAt)}；逾期默认使用原商品报价。`;
  }
  if (isOverPriceReviewDetail.value) {
    return `复核截止：${formatDateTime(dueAt)}。逾期会继续提醒楼长，但不会自动通过。`;
  }
  return `截止 ${formatDateTime(dueAt)}；逾期确认将纳入下月采购。`;
});
const showPurchaseReasonColumn = computed(() => isPurchaseApprovalDetail.value && !isSupplierQuoteDetail.value);
const dialogTitle = computed(() => (isSupplierQuoteDetail.value ? "报价任务" : isAfterSaleApprovalDetail.value ? "售后审核" : "审批处理"));
const isSupplierAwardPending = computed(() => selectedRow.value?.status === "AWARD_PENDING");
const supplierQuoteGuideTitle = computed(() => (isSupplierAwardPending.value ? "请到采购管理 / 供应商报价确认中标" : "请到采购管理 / 供应商报价上传价格"));
const supplierQuoteGuideText = computed(() =>
  isSupplierAwardPending.value
    ? "工作台展示中标待确认提醒。请在供应商报价页面查看中标清单和送达地址，并在 4 个工作日内确认或放弃；逾期系统会自动视为放弃并递补下一名供应商。"
    : "工作台仅作为待办提醒，不展示内部审批流程。报价单价、交期、替换商品和批量导入请在供应商报价页面完成。"
);
const supplierQuoteGuideButton = computed(() => (isSupplierAwardPending.value ? "去处理中标" : "去上传报价"));
const approvalActionOptions = computed<Array<{ label: string; value: WorkbenchAction }>>(() => {
  if (replacementConfirmDetail.value) {
    return [
      { label: "使用替换商品报价", value: "APPROVE" },
      { label: "使用原商品报价", value: "REJECT" }
    ];
  }
  if (selectedRow.value?.sourceType === "PLATFORM" || isAfterSaleApprovalDetail.value) {
    if (isAfterSaleSupplierProposal.value) {
      return [
        { label: "提交方案", value: "APPROVE" },
        { label: "驳回售后", value: "REJECT" }
      ];
    }
    if (isAfterSaleProposalConfirmation.value) {
      return [
        { label: "同意方案", value: "APPROVE" },
        { label: "退回供应商重提", value: "REJECT" }
      ];
    }
    return [
      { label: "通过", value: "APPROVE" },
      { label: "驳回", value: "REJECT" }
    ];
  }
  if (isOverPriceReviewDetail.value) {
    return [{ label: "通过超价项", value: "APPROVE" }];
  }
  const options: Array<{ label: string; value: WorkbenchAction }> = [
    { label: "通过", value: "APPROVE" }
  ];
  if (canReturnSelected.value) {
    options.push({ label: "退回", value: "RETURN" });
  }
  options.push({ label: "加签", value: "ADD_SIGN" });
  return options;
});
const previousNodeOptions = computed(() => {
  const currentNo = selectedRow.value?.currentNodeNo ?? 0;
  return detailFlowNodes.value.filter((node) => node.nodeNo > 0 && node.nodeNo < currentNo);
});
const needsReturnTarget = computed(
  () => isPurchaseApprovalDetail.value && approvalForm.action === "RETURN" && previousNodeOptions.value.length > 0
);
const approvalActionLabel = computed(() => {
  if (replacementConfirmDetail.value) {
    return "最终使用报价";
  }
  if (isAfterSaleSupplierProposal.value) {
    return "售后处理";
  }
  if (isAfterSaleProposalConfirmation.value) {
    return "方案确认";
  }
  return "审批意见";
});
const approvalCommentLabel = computed(() => {
  if (replacementConfirmDetail.value) {
    return "选择说明";
  }
  if (showAfterSaleProposalForm.value) {
    return "方案说明";
  }
  if (isAfterSaleProposalConfirmation.value) {
    return "确认意见";
  }
  if (isOverPriceReviewDetail.value) {
    return "价格合理原因";
  }
  return selectedRow.value?.actionable ? "意见说明" : "终止原因";
});
const approvalCommentPlaceholder = computed(() => {
  if (replacementConfirmDetail.value) {
    return "可填写选择原因，留空时系统会自动记录选择结果";
  }
  if (showAfterSaleProposalForm.value) {
    return "说明退换、维修、补发或更换商品方案；如更换商品，请补全下方商品和单价";
  }
  if (isAfterSaleProposalConfirmation.value) {
    return approvalForm.action === "APPROVE" ? "可填写同意说明" : "请写明不同意原因，提交后会退回供应商重提方案";
  }
  if (isOverPriceReviewDetail.value) {
    return "通过的超价商品必须写明价格仍合理的依据；驳回商品请在对应行填写原因";
  }
  return selectedRow.value?.actionable ? "请输入意见说明" : "请输入终止原因";
});
const submitButtonText = computed(() => {
  if (replacementConfirmDetail.value) {
    return "提交选择";
  }
  if (isAfterSaleSupplierProposal.value) {
    return approvalForm.action === "APPROVE" ? "提交方案" : "驳回售后";
  }
  if (isAfterSaleProposalConfirmation.value) {
    return approvalForm.action === "APPROVE" ? "同意方案" : "退回供应商";
  }
  if (isOverPriceReviewDetail.value) {
    return "提交复核";
  }
  return "提交";
});

watch(
  () => [activeFilter.value, query.keyword, query.statusText, query.dateRange?.join(",")],
  () => {
    page.pageNo = 1;
  }
);

onMounted(() => {
  void loadWorkbench();
  void loadUserOptions();
  void loadVisibleNotices();
});

async function loadWorkbench(): Promise<void> {
  loading.value = true;
  try {
    const pageSize = 500;
    let pageNo = 1;
    let total = 0;
    const allRows: WorkbenchRequestVO[] = [];
    do {
      const data = await getWorkbenchRequestsApi({ pageNo, pageSize });
      if (pageNo === 1) {
        total = data.page.total || 0;
        backendMetrics.value = data.metrics ?? [];
      }
      allRows.push(...data.page.records.map(normalizeRow));
      if (!data.page.records.length) {
        break;
      }
      pageNo += 1;
    } while (allRows.length < total);
    rows.value = allRows;
  } finally {
    loading.value = false;
  }
}

async function loadUserOptions(): Promise<void> {
  userOptions.value = await getWorkbenchUserOptionsApi();
}

async function loadVisibleNotices(): Promise<void> {
  noticeLoading.value = true;
  try {
    visibleNotices.value = await getWorkbenchNoticesApi();
  } finally {
    noticeLoading.value = false;
  }
}

async function loadManageNotices(): Promise<void> {
  noticeManageLoading.value = true;
  try {
    managedNotices.value = await getWorkbenchManageNoticesApi();
  } finally {
    noticeManageLoading.value = false;
  }
}

async function openNoticeEditor(): Promise<void> {
  noticeEditorVisible.value = true;
  resetNoticeForm();
  await loadManageNotices();
}

function editNotice(notice: WorkbenchNoticeVO): void {
  editingNoticeId.value = notice.id;
  noticeForm.title = notice.title;
  noticeForm.content = notice.content;
  noticeForm.pinned = Boolean(notice.pinned);
  noticeForm.status = notice.status === 0 ? 0 : 1;
  noticeForm.sortNo = notice.sortNo ?? 100;
}

function resetNoticeForm(): void {
  editingNoticeId.value = null;
  noticeForm.title = "";
  noticeForm.content = "";
  noticeForm.pinned = false;
  noticeForm.status = 1;
  noticeForm.sortNo = 100;
}

async function saveNotice(): Promise<void> {
  if (!noticeForm.title.trim()) {
    ElMessage.warning("请填写公告标题");
    return;
  }
  if (!noticeForm.content.trim()) {
    ElMessage.warning("请填写公告内容");
    return;
  }
  noticeSaving.value = true;
  try {
    if (editingNoticeId.value) {
      await updateWorkbenchNoticeApi(editingNoticeId.value, noticePayload());
      ElMessage.success("公告已更新");
    } else {
      await createWorkbenchNoticeApi(noticePayload());
      ElMessage.success("公告已新增");
    }
    resetNoticeForm();
    await loadManageNotices();
    await loadVisibleNotices();
  } finally {
    noticeSaving.value = false;
  }
}

async function removeNotice(notice: WorkbenchNoticeVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除公告“${notice.title}”？`, "删除公告", { type: "warning" });
  } catch {
    return;
  }
  await deleteWorkbenchNoticeApi(notice.id);
  ElMessage.success("公告已删除");
  if (editingNoticeId.value === notice.id) {
    resetNoticeForm();
  }
  await loadManageNotices();
  await loadVisibleNotices();
}

function noticePayload(): WorkbenchNoticeForm {
  return {
    title: noticeForm.title.trim(),
    content: noticeForm.content.trim(),
    pinned: noticeForm.pinned,
    status: noticeForm.status,
    sortNo: Number(noticeForm.sortNo || 0)
  };
}

async function openRequest(row: WorkbenchRequestVO): Promise<void> {
  selectedRow.value = row;
  detailVisible.value = true;
  flowStatusVisible.value = false;
  detailLoading.value = true;
  resetApprovalForm();
  try {
    detailData.value = await getWorkbenchRequestDetailApi(row.sourceType, row.sourceId);
    selectedRow.value = normalizeRow(detailData.value.summary);
    approvalItemRows.value = (detailData.value.detailRows ?? []).map(toApprovalItemRow);
    syncReplacementSelections(approvalItemRows.value);
    syncAfterSaleProposalForm();
    syncRow(selectedRow.value);
  } finally {
    detailLoading.value = false;
  }
}

async function submitApprovalAction(actionOverride?: WorkbenchAction): Promise<void> {
  let action = actionOverride || approvalForm.action;
  if (!selectedRow.value?.actionable && !(action === "TERMINATE" && canTerminateSelected.value)) {
    return;
  }
  if (action === "RETURN" && !canReturnSelected.value) {
    ElMessage.warning("系统发起的全国月度汇总单不支持退回。");
    approvalForm.action = "APPROVE";
    return;
  }
  if (action === "ADD_SIGN" && approvalForm.addSignUserId == null) {
    ElMessage.warning("请选择加签人。");
    return;
  }
  if ((["RETURN", "TERMINATE"].includes(action) || (action === "REJECT" && !replacementConfirmDetail.value)) && !approvalForm.comment.trim()) {
    ElMessage.warning(action === "TERMINATE" ? "终止时请填写终止原因。" : "退回或驳回时请填写处理意见。");
    return;
  }
  if (action === "TERMINATE") {
    const confirmed = await ElMessageBox.confirm("确认终止当前流程？终止后该单不会继续流转。", "终止流程确认", {
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
  if (!validateAfterSaleApproval(action)) {
    return;
  }

  const replacementDecisions = replacementConfirmDetail.value ? replacementDecisionPayload() : undefined;
  if (replacementConfirmDetail.value) {
    if (!replacementDecisions) {
      return;
    }
    action = replacementDecisions.some((decision) => decision.replacementApproved) ? "APPROVE" : "REJECT";
  }

  const itemDecisions = approvalItemRows.value
    .filter((row) => row.rejected && row.status === "ACTIVE")
    .map((row) => ({
      itemId: row.itemId,
      rejected: true,
      rejectReason: row.rejectReasonInput.trim()
    }));
  if (action === "APPROVE") {
    const missingReason = itemDecisions.some((item) => !item.rejectReason);
    if (missingReason) {
      ElMessage.warning("单项驳回时必须填写驳回原因。");
      return;
    }
    if (itemDecisions.length > 0) {
      const confirmed = await confirmItemRejectSubmit();
      if (!confirmed) {
        return;
      }
    }
    if (isOverPriceReviewDetail.value) {
      const rejectedIds = new Set(itemDecisions.map((item) => item.itemId));
      const hasPassedItem = approvalItemRows.value.some(
        (row) => row.status === "ACTIVE" && row.overPriceReviewRequired && !rejectedIds.has(row.itemId)
      );
      if (hasPassedItem && !meaningfulOverPriceComment(approvalForm.comment)) {
        ElMessage.warning("通过超价商品时，请填写具体的价格合理原因。");
        return;
      }
    }
  }

  submitLoading.value = true;
  try {
    const row = selectedRow.value;
    if (!row) {
      return;
    }
    const result = await approveWorkbenchRequestApi(row.sourceType, row.sourceId, {
      action,
      targetNodeNo: action === "RETURN" ? approvalForm.targetNodeNo : null,
      itemDecisions: action === "APPROVE" && !replacementConfirmDetail.value ? itemDecisions : undefined,
      replacementDecisions,
      addSignUser: approvalForm.addSignUserId == null ? "" : String(approvalForm.addSignUserId),
      comment: approvalForm.comment,
      ...afterSaleProposalPayload()
    });
    ElMessage.success(submitSuccessText(action));
    detailVisible.value = false;
    if (!["SAVE", "TERMINATE", "ADD_SIGN"].includes(action)) {
      await showSubmitNextApprovalDialog(result);
    }
    await loadWorkbench();
  } finally {
    submitLoading.value = false;
  }
}

function toApprovalItemRow(row: WorkbenchDetailRowVO): WorkbenchApprovalItemRow {
  const alreadyRejected = row.status === "REJECTED";
  return {
    ...row,
    rejected: alreadyRejected,
    rejectReasonInput: alreadyRejected ? row.rejectReason || row.remark || "" : ""
  };
}

function handleItemRejectChange(row: WorkbenchApprovalItemRow): void {
  if (!row.rejected) {
    row.rejectReasonInput = "";
  }
}

function meaningfulOverPriceComment(value: string): boolean {
  const normalized = value.trim().replace(/[，。；、\s]/g, "");
  return normalized.length >= 4 && !["同意", "通过", "确认", "价格合理", "同意通过"].includes(normalized);
}

function formatPercent(value?: number | null): string {
  if (value == null || !Number.isFinite(Number(value))) {
    return "-";
  }
  return `${Number(value).toFixed(1)}%`;
}

function syncReplacementSelections(rows: WorkbenchApprovalItemRow[]): void {
  clearReplacementSelections();
  rows
    .filter(isAwardedReplacementRow)
    .forEach((row) => {
      const status = String(row.replacementReviewStatus || "").toUpperCase();
      if (status === "APPROVED") {
        replacementSelections[replacementSelectionKey(row)] = "REPLACEMENT";
      } else if (["REJECTED", "AUTO_REJECTED"].includes(status)) {
        replacementSelections[replacementSelectionKey(row)] = "ORIGINAL";
      }
    });
}

function clearReplacementSelections(): void {
  Object.keys(replacementSelections).forEach((key) => {
    delete replacementSelections[key];
  });
}

function isAwardedReplacementRow(row: WorkbenchDetailRowVO): boolean {
  return Boolean(row.replacementQuote && row.awarded && row.quoteItemId != null);
}

function canSelectReplacement(row: WorkbenchDetailRowVO): boolean {
  return Boolean(selectedRow.value?.actionable && isAwardedReplacementRow(row));
}

function selectReplacementOption(row: WorkbenchDetailRowVO, selection: ReplacementSelection): void {
  if (!canSelectReplacement(row)) {
    return;
  }
  replacementSelections[replacementSelectionKey(row)] = selection;
}

function replacementSelectionKey(row: WorkbenchDetailRowVO): string {
  return row.quoteItemId == null ? replacementRowKey(row) : String(row.quoteItemId);
}

function replacementSelection(row: WorkbenchDetailRowVO): ReplacementSelection | undefined {
  return replacementSelections[replacementSelectionKey(row)];
}

function replacementDecisionPayload(): Array<{ quoteItemId?: number | null; replacementApproved: boolean }> | undefined {
  const rows = approvalItemRows.value.filter(isAwardedReplacementRow);
  if (rows.length === 0) {
    ElMessage.warning("当前没有待确认的中标替换商品。");
    return undefined;
  }
  const missingRows = rows.filter((row) => !replacementSelection(row));
  if (missingRows.length > 0) {
    ElMessage.warning(`请先选择：${missingRows.map(replacementDecisionRowText).join("、")}`);
    return undefined;
  }
  return rows.map((row) => ({
    quoteItemId: row.quoteItemId,
    replacementApproved: replacementSelection(row) === "REPLACEMENT"
  }));
}

function replacementDecisionRowText(row: WorkbenchDetailRowVO): string {
  return replacementCardTitle(row).replace(" · 当前中标", "");
}

function itemReasonText(row: WorkbenchApprovalItemRow): string {
  if (row.status === "REJECTED" || row.rejected) {
    return row.rejectReason || row.rejectReasonInput || row.remark || "-";
  }
  if (row.status === "RETURNED") {
    return row.remark || row.purchaseReason || "-";
  }
  return row.purchaseReason || row.remark || "-";
}

function itemStatusDisplayText(row: WorkbenchDetailRowVO): string {
  if (row.status === "RETURNED") {
    return "退货";
  }
  return displayStatusText(row.statusText, row.status);
}

async function confirmItemRejectSubmit(): Promise<boolean> {
  const rejectedRows = approvalItemRows.value.filter((row) => row.rejected && row.status === "ACTIVE");
  const itemText = rejectedItemSummary(rejectedRows);
  const reqNo = selectedRow.value?.reqNo || "当前申请";
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

function rejectedItemDisplay(row: WorkbenchApprovalItemRow): string {
  const name = row.name || row.replacementItemName || row.originalItemName || "商品明细";
  return row.lineNo ? `${row.lineNo}. ${name}` : name;
}

function rejectedItemSummary(rows: WorkbenchApprovalItemRow[]): string {
  if (rows.length === 0) {
    return "已勾选商品";
  }
  const visibleItems = rows.slice(0, 3).map(rejectedItemDisplay);
  return rows.length > 3 ? `${visibleItems.join("、")} 等 ${rows.length} 项` : visibleItems.join("、");
}

async function toggleFollow(row: WorkbenchRequestVO): Promise<void> {
  const nextFollowed = !row.followed;
  followLoadingId.value = row.id;
  try {
    await setWorkbenchFollowApi({
      sourceType: row.sourceType,
      sourceId: row.sourceId,
      followed: nextFollowed
    });
    row.followed = nextFollowed;
    syncRow(row);
  } finally {
    followLoadingId.value = "";
  }
}

async function goSupplierQuotePage(): Promise<void> {
  const reqNo = selectedRow.value?.reqNo;
  detailVisible.value = false;
  await router.push({
    path: "/procurement/quotes",
    query: reqNo ? { reqNo } : undefined
  });
}

function metricTile(key: string, label: string, note: string, tone: string, filter: QueueFilter) {
  const metric = backendMetrics.value.find((item) => item.key === key);
  const value = metric?.value ?? countRows((row) => row.relation === key);
  return { key, label, note, tone, filter, value };
}

function normalizeRow(row: WorkbenchRequestVO): WorkbenchRequestVO {
  return {
    ...row,
    currentApproverNames: displayPersonNames(row.currentApproverNames),
    statusText: displayStatusText(row.statusText, row.status),
    arrivedAt: formatDateTime(row.arrivedAt),
    followed: Boolean(row.followed),
    actionable: Boolean(row.actionable),
    terminable: Boolean(row.terminable),
    returnable: row.returnable !== false
  };
}

function shortReqNo(reqNo?: string): string {
  if (!reqNo) {
    return "-";
  }
  if (reqNo.length <= 12) {
    return reqNo;
  }
  return `${reqNo.slice(0, 2)}…${reqNo.slice(-8)}`;
}

function currentNodeDisplay(row?: WorkbenchRequestVO | null): string {
  if (!row) {
    return "-";
  }
  const approverNames = displayPersonNames(row.currentApproverNames);
  if (approverNames) {
    return approverNames;
  }
  return row.currentNode || "-";
}

function displayPersonNames(value?: string): string {
  return (value || "")
    .split(/[,，、/]/u)
    .map((name) => name.trim().replace(/[\s\u3000]+\d{3,}$/u, ""))
    .filter(Boolean)
    .join("、");
}

function syncRow(nextRow: WorkbenchRequestVO): void {
  const index = rows.value.findIndex((row) => row.id === nextRow.id);
  if (index >= 0) {
    rows.value[index] = { ...rows.value[index], ...nextRow };
  }
}

function setFilter(filter: QueueFilter): void {
  activeFilter.value = filter;
}

function goOutsourcing(path: string): void {
  void router.push(path);
}

function resetQuery(): void {
  query.keyword = "";
  query.statusText = "";
  query.dateRange = [];
  page.pageNo = 1;
}

function handlePageChange(pageNo: number): void {
  page.pageNo = pageNo;
}

function handleSizeChange(pageSize: number): void {
  page.pageSize = pageSize;
  page.pageNo = 1;
}

function matchesFilter(row: WorkbenchRequestVO, filter: QueueFilter): boolean {
  if (filter === "all") return true;
  if (filter.startsWith("module:")) return row.module === filter.slice("module:".length);
  if (filter === "followed") return row.followed;
  return row.relation === filter;
}

function compareArrivedAtDesc(left: WorkbenchRequestVO, right: WorkbenchRequestVO): number {
  const leftTime = parseLocalDateTime(left.arrivedAt)?.getTime() ?? 0;
  const rightTime = parseLocalDateTime(right.arrivedAt)?.getTime() ?? 0;
  if (leftTime !== rightTime) {
    return rightTime - leftTime;
  }
  return String(right.reqNo || "").localeCompare(String(left.reqNo || ""));
}

function countRows(predicate: (row: WorkbenchRequestVO) => boolean): number {
  return rows.value.filter(predicate).length;
}

function businessUnitName(row: WorkbenchRequestVO): string {
  const moduleName = (row.moduleName || "").trim();
  if (moduleName) {
    return moduleName.endsWith("管理") ? moduleName : `${moduleName}管理`;
  }
  return businessUnitFallbackNames[row.module] || row.module;
}

function statusTagType(status?: string): TagType {
  const normalized = (status || "").toUpperCase();
  if (["APPROVED", "DONE", "COMPLETED", "ACTIVE", "VALID", "SUBMITTED", "CONFIRMED", "AWARD_CONFIRMED", "AWARDED"].includes(normalized)) {
    return "success";
  }
  if (["REJECTED", "FAILED", "TERMINATED"].includes(normalized)) {
    return "danger";
  }
  if (["RETURNED", "DRAFT", "QUOTING", "PENDING", "PROPOSAL_PENDING", "PENDING_APPROVAL", "AWARD_PENDING"].includes(normalized)) {
    return "warning";
  }
  return "info";
}

function displayStatusText(statusText?: string, status?: string): string {
  const normalized = (statusText || status || "").toUpperCase();
  const map: Record<string, string> = {
    AWARD_PENDING: "中标待确认",
    AWARD_CONFIRMED: "中标",
    AWARD_WAIVED: "已放弃",
    DRAFT: "草稿",
    PENDING: "待处理",
    PROPOSAL_PENDING: "方案待确认",
    PENDING_APPROVAL: "待审批",
    APPROVED: "已通过",
    REJECTED: "已驳回",
    RETURNED: "已退回",
    TERMINATED: "已终止",
    SUBMITTED: "已提交",
    QUOTING: "询价中",
    AWARDED: "已中标",
    QUOTED: "已报价",
    NOT_AWARDED: "未中标",
    CONFIRMED: "已确认",
    DONE: "已完成",
    COMPLETED: "已完成",
    ACTIVE: "有效",
    VALID: "有效",
    SUPPLEMENTED: "已补充"
  };
  return map[normalized] || statusText || status || "-";
}

function actionText(action?: string): string {
  const map: Record<string, string> = {
    SUBMIT: "提交",
    APPROVE: "通过",
    REJECT: "驳回",
    RETURN: "退回",
    ADD_SIGN: "加签",
    SAVE: "暂存",
    TERMINATE: "终止",
    APPROVE_REPLACEMENT: "使用替换商品报价",
    REJECT_REPLACEMENT: "使用原商品报价",
    CONFIRM_REPLACEMENT_BY_ITEM: "分项确认替换商品"
  };
  return map[(action || "").toUpperCase()] || action || "-";
}

function flowTagType(state?: string): TagType {
  const map: Record<string, TagType> = {
    done: "success",
    current: "warning",
    pending: "info",
    rejected: "danger",
    returned: "warning"
  };
  return map[state || ""] ?? "info";
}

function flowStateText(state?: string): string {
  const map: Record<string, string> = {
    done: "已提交",
    current: "当前节点",
    pending: "未开始",
    rejected: "已驳回",
    returned: "已退回"
  };
  return map[state || ""] ?? "未开始";
}

function returnTargetLabel(node: WorkbenchFlowNodeVO): string {
  const owner = node.meta ? node.meta.split("/")[0].trim() : "";
  return owner ? `${node.nodeNo} - ${node.name}（${owner}）` : `${node.nodeNo} - ${node.name}`;
}

function queueRowClassName({ row }: { row: WorkbenchRequestVO }): string {
  return row.relation === "todo" ? "is-todo-row" : "";
}

function resetApprovalForm(): void {
  approvalForm.action = "APPROVE";
  approvalForm.targetNodeNo = null;
  approvalForm.addSignUserId = null;
  approvalForm.comment = "";
  resetAfterSaleProposalForm();
  approvalItemRows.value = [];
  clearReplacementSelections();
}

function resetAfterSaleProposalForm(): void {
  afterSaleProposalForm.proposalType = "REPLACE_ITEM";
  afterSaleProposalForm.replacementItemCode = "";
  afterSaleProposalForm.replacementItemName = "";
  afterSaleProposalForm.replacementSpecModel = "";
  afterSaleProposalForm.replacementUnit = "";
  afterSaleProposalForm.replacementUnitPrice = null;
  afterSaleProposalForm.replacementLineAmount = null;
}

function syncAfterSaleProposalForm(): void {
  resetAfterSaleProposalForm();
  if (!isAfterSaleApprovalDetail.value) {
    return;
  }
  const row = afterSaleDetailRow.value;
  if (!row) {
    return;
  }
  afterSaleProposalForm.proposalType = row.supplierProposalType || "REPLACE_ITEM";
  afterSaleProposalForm.replacementItemCode = row.supplierReplacementItemCode || "";
  afterSaleProposalForm.replacementItemName = row.supplierReplacementItemName || row.replacementItemName || "";
  afterSaleProposalForm.replacementSpecModel = row.supplierReplacementSpecModel || row.replacementSpec || "";
  afterSaleProposalForm.replacementUnit = row.supplierReplacementUnit || row.unit || "";
  afterSaleProposalForm.replacementUnitPrice = row.supplierReplacementUnitPrice == null ? null : Number(row.supplierReplacementUnitPrice);
  afterSaleProposalForm.replacementLineAmount = row.supplierReplacementLineAmount == null ? null : Number(row.supplierReplacementLineAmount);
}

function syncAfterSaleProposalAmount(): void {
  const unitPrice = Number(afterSaleProposalForm.replacementUnitPrice ?? 0);
  const qty = Number(afterSaleDetailRow.value?.qty || 1);
  if (Number.isFinite(unitPrice) && unitPrice > 0 && Number.isFinite(qty) && qty > 0) {
    afterSaleProposalForm.replacementLineAmount = Number((unitPrice * qty).toFixed(2));
  }
}

function validateAfterSaleApproval(action: WorkbenchAction): boolean {
  if (!showAfterSaleProposalForm.value || action !== "APPROVE") {
    return true;
  }
  const replacementName = afterSaleProposalForm.replacementItemName.trim();
  const replacementType = afterSaleProposalForm.proposalType === "REPLACE_ITEM";
  if (!approvalForm.comment.trim() && !replacementName) {
    ElMessage.warning("请填写供应商处理方案。");
    return false;
  }
  if (replacementType || replacementName) {
    if (!replacementName) {
      ElMessage.warning("更换商品方案必须填写方案商品。");
      return false;
    }
    const unitPrice = Number(afterSaleProposalForm.replacementUnitPrice);
    if (!Number.isFinite(unitPrice) || unitPrice <= 0) {
      ElMessage.warning("更换商品方案必须填写大于 0 的方案单价。");
      return false;
    }
    const limit = Number(afterSaleDetailRow.value?.priceIncreaseLimit);
    if (Number.isFinite(limit) && unitPrice > limit) {
      ElMessage.warning("方案单价最多只能比当前单价高 400 元。");
      return false;
    }
  }
  return true;
}

function afterSaleProposalPayload(): Record<string, unknown> {
  if (!showAfterSaleProposalForm.value) {
    return {};
  }
  return {
    afterSaleProposalType: afterSaleProposalForm.proposalType,
    afterSaleReplacementItemCode: afterSaleProposalForm.replacementItemCode.trim(),
    afterSaleReplacementItemName: afterSaleProposalForm.replacementItemName.trim(),
    afterSaleReplacementSpecModel: afterSaleProposalForm.replacementSpecModel.trim(),
    afterSaleReplacementUnit: afterSaleProposalForm.replacementUnit.trim(),
    afterSaleReplacementUnitPrice: afterSaleProposalForm.replacementUnitPrice,
    afterSaleReplacementLineAmount: afterSaleProposalForm.replacementLineAmount
  };
}

function formatDateTime(value?: string): string {
  if (!value) {
    return "";
  }
  return value.replace("T", " ").slice(0, 16);
}

function moneyText(value?: number | string | null): string {
  if (value == null || value === "") {
    return "-";
  }
  const amount = Number(value);
  if (!Number.isFinite(amount)) {
    return String(value);
  }
  return `¥${amount.toLocaleString("zh-CN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`;
}

function hasText(value?: string | null): boolean {
  return Boolean(String(value ?? "").trim());
}

function detailText(value?: string | number | null): string {
  return String(value ?? "").trim() || "-";
}

function normalizeOptionalUrl(url?: string | null): string {
  const text = String(url ?? "").trim();
  if (/^https?:\/\//i.test(text)) {
    return text;
  }
  return `https://${text}`;
}

function replacementRowKey(row: WorkbenchDetailRowVO): string {
  return [
    row.lineNo ?? "-",
    row.supplierName ?? "-",
    row.replacementItemName ?? row.name ?? "-"
  ].join("|");
}

function replacementCardTitle(row: WorkbenchDetailRowVO): string {
  const line = row.lineNo == null ? "替换报价" : `第 ${row.lineNo} 项替换报价`;
  return row.awarded ? `${line} · 当前中标` : line;
}

function quantityText(row: WorkbenchDetailRowVO): string {
  const qty = row.qty == null || row.qty === "" ? "-" : String(row.qty);
  return qty === "-" ? "-" : `${qty}${row.unit || ""}`;
}

function submitSuccessText(action: WorkbenchAction): string {
  if (action === "SAVE") {
    return "已暂存处理意见。";
  }
  if (action === "TERMINATE") {
    return "流程已终止。";
  }
  if (replacementConfirmDetail.value) {
    return "已提交各组报价选择。";
  }
  return "审批已提交。";
}

function supplierContactText(row: WorkbenchDetailRowVO): string {
  const clean = (value?: string) => {
    const text = (value || "").trim();
    return text === "-" ? "" : text;
  };
  const name = clean(row.supplierContactName);
  const phone = clean(row.supplierContactPhone);
  return [name, phone].filter(Boolean).join(" / ") || "-";
}

function replacementSupplierText(row: WorkbenchDetailRowVO): string {
  const supplier = (row.supplierName || "").trim();
  const contact = supplierContactText(row);
  if (supplier && supplier !== "-" && contact !== "-") {
    return `${supplier}（${contact}）`;
  }
  return supplier && supplier !== "-" ? supplier : contact;
}

function replacementSpecChanged(row: WorkbenchDetailRowVO): boolean {
  const original = String(row.originalSpec || row.spec || "").trim().toLowerCase();
  const replacement = String(row.replacementSpec || "").trim().toLowerCase();
  return Boolean(original && replacement && original !== replacement);
}

function replacementPriceDifference(row: WorkbenchDetailRowVO): number {
  const original = Number(row.quoteUnitPrice || 0);
  const replacement = Number(row.replacementUnitPrice || 0);
  return Number.isFinite(original) && Number.isFinite(replacement)
    ? Number((replacement - original).toFixed(2))
    : 0;
}

function replacementPriceRate(row: WorkbenchDetailRowVO): number | null {
  const original = Number(row.quoteUnitPrice || 0);
  if (!Number.isFinite(original) || original <= 0) {
    return null;
  }
  return Number((replacementPriceDifference(row) / original * 100).toFixed(1));
}

function replacementHasDifference(row: WorkbenchDetailRowVO): boolean {
  return replacementSpecChanged(row) || replacementPriceDifference(row) !== 0;
}

function replacementDifferenceText(row: WorkbenchDetailRowVO): string {
  const differences: string[] = [];
  if (replacementSpecChanged(row)) {
    differences.push(`规格 ${detailText(row.originalSpec || row.spec)} → ${detailText(row.replacementSpec)}`);
  }
  const priceDifference = replacementPriceDifference(row);
  const priceRate = replacementPriceRate(row);
  if (priceDifference !== 0) {
    differences.push(`单价 ${priceDifference > 0 ? "+" : "-"}${moneyText(Math.abs(priceDifference))}${priceRate == null ? "" : `（${priceRate > 0 ? "+" : ""}${priceRate.toFixed(1)}%）`}`);
  }
  return differences.join("；");
}

function remainingDeadlineText(value?: string | null): string {
  const deadline = parseLocalDateTime(value || "");
  if (!deadline) {
    return "-";
  }
  const remainingMinutes = Math.ceil((deadline.getTime() - Date.now()) / 60000);
  if (remainingMinutes <= 0) {
    return `已超时 ${durationText(Math.abs(remainingMinutes))}`;
  }
  return durationText(remainingMinutes);
}

function durationText(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  if (hours <= 0) {
    return `${restMinutes}分钟`;
  }
  return restMinutes > 0 ? `${hours}小时${restMinutes}分钟` : `${hours}小时`;
}

function parseLocalDateTime(value?: string): Date | null {
  if (!value) {
    return null;
  }
  const normalized = value.includes("T") ? value : value.replace(" ", "T");
  const date = new Date(normalized);
  return Number.isNaN(date.getTime()) ? null : date;
}

function weekStart(now: Date): Date {
  const start = new Date(now);
  const day = start.getDay() || 7;
  start.setHours(0, 0, 0, 0);
  start.setDate(start.getDate() - day + 1);
  return start;
}
</script>

<style scoped>
.metric-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.metric-tile {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 68px;
  padding: 12px 14px;
  overflow: hidden;
  border: 1px solid #e1e8f5;
  border-radius: 6px;
  background: #fff;
  color: #142a52;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease;
}

.metric-tile::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: #9fb5dc;
  content: "";
}

.metric-tile:hover,
.metric-tile.is-active {
  border-color: #2f6bff;
  background: #f8fbff;
  box-shadow: 0 8px 18px rgba(27, 83, 204, 0.08);
}

.metric-tile.is-active::before {
  background: #2f6bff;
}

.metric-copy {
  min-width: 0;
}

.metric-copy span {
  display: block;
  overflow: hidden;
  color: #243f70;
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-tile strong {
  color: #102a5c;
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
  letter-spacing: 0;
}

.metric-copy small {
  display: block;
  overflow: hidden;
  margin-top: 5px;
  color: #8190aa;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-tile--primary::before {
  background: #2f6bff;
}

.metric-tile--success::before {
  background: #28a86b;
}

.metric-tile--warning::before {
  background: #d8891f;
}

.metric-tile--danger::before {
  background: #d94a4a;
}

.metric-tile--neutral::before {
  background: #7b8aa5;
}

.outsourcing-workstream {
  margin-bottom: 12px;
}

.outsourcing-todo-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid #e4eaf4;
  border-radius: 6px;
  overflow: hidden;
}

.outsourcing-todo-item {
  position: relative;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto 16px;
  align-items: center;
  gap: 10px;
  min-height: 78px;
  padding: 12px 14px;
  border: 0;
  border-right: 1px solid #e4eaf4;
  background: #fff;
  color: #16315f;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.outsourcing-todo-item:last-child {
  border-right: 0;
}

.outsourcing-todo-item::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: #2f6bff;
  content: "";
}

.outsourcing-todo-item:hover {
  background: #f7faff;
}

.outsourcing-todo-icon {
  display: grid;
  width: 36px;
  height: 36px;
  border-radius: 6px;
  background: #edf4ff;
  color: #2f6bff;
  place-items: center;
}

.outsourcing-todo-copy {
  min-width: 0;
}

.outsourcing-todo-copy strong,
.outsourcing-todo-copy small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outsourcing-todo-copy strong {
  font-size: 14px;
}

.outsourcing-todo-copy small {
  margin-top: 5px;
  color: #7a8ba8;
  font-size: 12px;
}

.outsourcing-todo-item b {
  min-width: 28px;
  color: #174da8;
  font-size: 22px;
  text-align: center;
}

.outsourcing-todo-arrow {
  color: #9aa9bf;
}

.outsourcing-todo-item--warning::before {
  background: #d8891f;
}

.outsourcing-todo-item--warning .outsourcing-todo-icon {
  background: #fff5e8;
  color: #c87412;
}

.outsourcing-todo-item--success::before {
  background: #28a86b;
}

.outsourcing-todo-item--success .outsourcing-todo-icon {
  background: #eaf8f1;
  color: #218b5a;
}

.outsourcing-todo-item--danger::before {
  background: #d94a4a;
}

.outsourcing-todo-item--danger .outsourcing-todo-icon {
  background: #fff0f0;
  color: #c53d3d;
}

.workbench-grid {
  display: grid;
  gap: 12px;
  margin-bottom: 12px;
}

.workbench-insight-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.95fr) minmax(300px, 1fr) minmax(320px, 1.1fr);
  gap: 12px;
  align-items: stretch;
  margin-bottom: 12px;
}

.queue-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
  padding: 4px;
  border: 1px solid #e6edf8;
  border-radius: 8px;
  background: #f7f9fd;
}

.queue-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #24406f;
  font-size: 13px;
  cursor: pointer;
}

.queue-tabs button span {
  min-width: 18px;
  padding: 0 5px;
  border-radius: 10px;
  background: #edf3ff;
  color: #2f6bff;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

.queue-tabs button.is-active {
  border-color: #cbd9f3;
  background: #fff;
  color: #1e54cf;
  box-shadow: 0 3px 8px rgba(24, 50, 95, 0.06);
}

.queue-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.queue-search {
  flex: 1 1 360px;
  min-width: 260px;
}

.status-select {
  flex: 0 0 150px;
}

.date-range {
  flex: 0 1 340px;
  min-width: 280px;
  max-width: 360px;
}

.reset-query-button {
  flex: 0 0 72px;
}

.queue-search,
.status-select,
.date-range,
.reset-query-button {
  width: 100%;
  min-width: 0;
}

.queue-table :deep(.is-todo-row td:first-child) {
  border-left: 3px solid #2f6bff;
}

.queue-table :deep(.el-table__header th) {
  background: #f7faff;
  color: #415579;
  font-size: 13px;
  font-weight: 700;
}

.queue-table :deep(.el-table__cell) {
  color: #263a60;
  font-size: 13px;
}

.queue-table :deep(.el-table__row) {
  height: 44px;
}

.follow-button {
  color: #9aa9c4;
}

.follow-button.is-followed {
  color: #ff9f1c;
}

.request-link {
  padding: 0;
  font-weight: 600;
}

.node-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
  line-height: 1.35;
}

.node-cell span,
.node-cell small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-cell small {
  color: #64748b;
  font-size: 12px;
}

.pager-box {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.week-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.week-summary div {
  min-width: 0;
  padding: 10px;
  border: 1px solid #e5edf8;
  border-radius: 6px;
  background: #f8fbff;
}

.week-summary span {
  display: block;
  overflow: hidden;
  color: #6b7891;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.week-summary strong {
  display: block;
  margin-top: 5px;
  color: #102a5c;
  font-size: 22px;
  line-height: 1;
}

.mini-bars,
.progress-list,
.notice-list {
  display: grid;
  gap: 8px;
}

.mini-bar-row,
.progress-row {
  display: grid;
  grid-template-columns: minmax(90px, 1fr) minmax(80px, 1.4fr) 42px;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.mini-bar-row span,
.progress-row span {
  overflow: hidden;
  color: #31466f;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini-bar-row div {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf2f8;
}

.mini-bar-row i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #2f6bff;
}

.mini-bar-row strong,
.progress-row strong {
  color: #102a5c;
  text-align: right;
}

.notice-board {
  min-height: 150px;
}

.notice-item {
  padding: 10px 0;
  border-bottom: 1px solid #edf2f8;
}

.notice-item:first-child {
  padding-top: 0;
}

.notice-item:last-child {
  border-bottom: 0;
}

.notice-item div {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.notice-item strong {
  overflow: hidden;
  color: #17315d;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-item p {
  margin: 6px 0;
  color: #53647f;
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
}

.notice-item small {
  color: #8a99b4;
}

.notice-editor {
  display: grid;
  gap: 14px;
}

.notice-form {
  padding: 12px;
  border: 1px solid #e6edf8;
  border-radius: 8px;
  background: #f8fbff;
}

.notice-form-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px;
  gap: 12px;
}

.notice-form-actions {
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
}

.notice-manage-table :deep(.el-table__header th) {
  background: #f7faff;
  color: #415579;
}

.detail-body {
  min-height: 520px;
}

.detail-summary {
  margin-bottom: 14px;
}

.replacement-review-meta {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin: 0 0 14px;
  overflow: hidden;
  border: 1px solid #dfe7f2;
  border-radius: 6px;
  background: #f8fbff;
}

.replacement-review-meta > div {
  min-width: 0;
  padding: 12px 14px;
  border-right: 1px solid #dfe7f2;
}

.replacement-review-meta > div:last-child {
  border-right: 0;
}

.replacement-review-meta dt {
  color: #6f7f99;
  font-size: 12px;
}

.replacement-review-meta dd {
  overflow: hidden;
  margin: 5px 0 0;
  color: #17315d;
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.replacement-review-meta dd.is-warning {
  color: #d97706;
}

.dialog-section {
  margin-top: 14px;
}

.dialog-section-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #18325f;
}

.flow-track {
  display: grid;
  grid-template-columns: repeat(4, minmax(210px, 1fr));
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.flow-card {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
  min-height: 78px;
  padding: 12px;
  border: 1px solid #dde6f5;
  border-radius: 6px;
  background: #fff;
}

.flow-card.is-done {
  border-color: #9ee0b7;
  background: #f3fbf6;
}

.flow-card.is-current {
  border-color: #ffb347;
  background: #fff9eb;
}

.flow-card.is-rejected,
.flow-card.is-returned {
  border-color: #ffc5c5;
  background: #fff6f6;
}

.flow-index {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #eaf1ff;
  color: #2f6bff;
  font-weight: 700;
}

.flow-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
}

.flow-title-row strong {
  overflow: hidden;
  color: #1d335d;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-content p {
  margin: 8px 0 0;
  color: #667794;
  font-size: 12px;
  line-height: 1.4;
}

.detail-table {
  border: 1px solid #e4ebf6;
  border-radius: 6px;
}

.over-price-value {
  color: var(--el-color-danger);
}

.replacement-compare-list {
  display: grid;
  gap: 12px;
}

.replacement-compare-card {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #dbe5f2;
  border-radius: 6px;
  background: #fff;
}

.replacement-comparison-stage {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 44px minmax(0, 1fr);
  gap: 10px;
  align-items: stretch;
}

.replacement-vs {
  display: grid;
  place-items: center;
  color: #2f6bff;
  font-size: 18px;
  font-weight: 800;
}

.replacement-compare-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.replacement-compare-head strong {
  display: block;
  color: #102a5c;
  font-size: 14px;
}

.replacement-compare-head span {
  display: block;
  margin-top: 4px;
  color: #687891;
  font-size: 12px;
}

.replacement-option {
  padding: 10px 12px;
  border: 1px solid #e1e8f5;
  border-radius: 6px;
  background: #f8fbff;
  outline: none;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}

.replacement-option .is-different {
  color: #d92d20;
}

.replacement-option.is-selectable {
  cursor: pointer;
}

.replacement-option.is-selectable:hover,
.replacement-option.is-selectable:focus-visible {
  border-color: #86a8f8;
  box-shadow: 0 0 0 2px rgba(47, 109, 246, 0.12);
}

.replacement-option.is-replacement {
  border-color: #b7e4cb;
  background: #f2fbf5;
}

.replacement-option.is-replacement.is-selectable:hover,
.replacement-option.is-replacement.is-selectable:focus-visible {
  border-color: #76c990;
  box-shadow: 0 0 0 2px rgba(52, 168, 83, 0.12);
}

.replacement-option.is-selected {
  border-color: #2f6df6;
  background: #f5f8ff;
  box-shadow: 0 0 0 1px rgba(47, 109, 246, 0.18);
}

.replacement-option.is-replacement.is-selected {
  border-color: #34a853;
  background: #effaf3;
  box-shadow: 0 0 0 1px rgba(52, 168, 83, 0.18);
}

.replacement-option-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.replacement-option-title {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.replacement-option-head strong {
  color: #102a5c;
  font-size: 13px;
}

.replacement-option-title span {
  color: #7a8aa3;
  font-size: 12px;
}

.replacement-field-grid {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr) 80px minmax(0, 1fr);
  gap: 7px 10px;
  align-items: start;
}

.replacement-field-grid span {
  color: #7a8aa3;
  font-size: 12px;
}

.replacement-field-grid strong {
  min-width: 0;
  color: #1f2f4d;
  font-size: 13px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.replacement-quote-remark {
  padding: 8px 10px;
  border-radius: 6px;
  background: #f6f8fb;
  color: #5e718d;
  font-size: 12px;
  line-height: 1.6;
}

.replacement-difference-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #f7c8c8;
  border-radius: 6px;
  background: #fff6f6;
  color: #b42318;
  font-size: 13px;
}

.replacement-difference-banner strong {
  flex: 0 0 auto;
}

.action-section {
  border-top: 1px solid #e8eef8;
  padding-top: 14px;
}

.quote-guide-section {
  border-top: 1px solid #e8eef8;
  padding-top: 14px;
}

.quote-guide-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #d8e5fb;
  border-radius: 6px;
  background: #f7faff;
}

.quote-guide-card strong {
  color: #18325f;
  font-size: 14px;
}

.quote-guide-card p {
  margin: 6px 0 0;
  color: #667794;
  font-size: 13px;
  line-height: 1.55;
}

.approval-action-form {
  padding-top: 2px;
}

.approval-deadline-alert {
  margin-bottom: 12px;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 260px));
  gap: 12px;
}

.action-grid :deep(.el-select) {
  width: 100%;
}

.after-sale-plan-descriptions {
  margin-bottom: 2px;
}

.after-sale-plan-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 12px;
}

.after-sale-plan-grid :deep(.el-input-number) {
  width: 100%;
}

.inline-flow-status {
  margin-bottom: 18px;
}

.inline-flow-map {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.inline-flow-node {
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid #dde6f5;
  border-radius: 6px;
  background: #fff;
}

.inline-flow-node.is-current {
  border-color: #f8c471;
  background: #fffaf0;
}

.inline-flow-node.is-done {
  border-color: #9ee0b7;
  background: #f3fbf6;
}

.inline-flow-node.is-rejected,
.inline-flow-node.is-returned {
  border-color: #ffc5c5;
  background: #fff6f6;
}

.inline-flow-node span,
.inline-flow-node strong,
.inline-flow-node small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inline-flow-node span {
  color: #2f6bff;
  font-size: 12px;
  font-weight: 700;
}

.inline-flow-node strong {
  margin-top: 4px;
  color: #18325f;
  font-size: 13px;
}

.inline-flow-node small {
  margin-top: 4px;
  color: #667794;
  font-size: 12px;
}

.inline-flow-records {
  border-radius: 6px;
}

.flow-status-section + .flow-status-section {
  margin-top: 18px;
}

.flow-map {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 14px;
  padding: 18px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.flow-map-node {
  position: relative;
  min-height: 118px;
  padding: 14px;
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

.flow-map-node.is-rejected,
.flow-map-node.is-returned {
  border-color: #fca5a5;
  background: #fef2f2;
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

@media (max-width: 1280px) {
  .replacement-review-meta {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .metric-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .outsourcing-todo-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .outsourcing-todo-item:nth-child(2) {
    border-right: 0;
  }

  .outsourcing-todo-item:nth-child(-n + 2) {
    border-bottom: 1px solid #e4eaf4;
  }

  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .workbench-insight-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .replacement-review-meta,
  .replacement-comparison-stage {
    grid-template-columns: 1fr;
  }

  .replacement-vs {
    min-height: 28px;
  }

  .metric-strip {
    grid-template-columns: 1fr;
  }

  .outsourcing-todo-grid {
    grid-template-columns: 1fr;
  }

  .outsourcing-todo-item,
  .outsourcing-todo-item:nth-child(2) {
    border-right: 0;
    border-bottom: 1px solid #e4eaf4;
  }

  .outsourcing-todo-item:last-child {
    border-bottom: 0;
  }

  .queue-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .queue-search,
  .status-select,
  .date-range,
  .reset-query-button {
    flex-basis: auto;
    max-width: none;
    min-width: 0;
  }

  .flow-track {
    grid-template-columns: 1fr;
  }

  .action-grid {
    grid-template-columns: 1fr;
  }

  .quote-guide-card {
    align-items: stretch;
    flex-direction: column;
  }

  .replacement-field-grid {
    grid-template-columns: 76px minmax(0, 1fr);
  }

  .week-summary,
  .notice-form-grid,
  .notice-form-actions {
    grid-template-columns: 1fr;
  }
}
</style>
