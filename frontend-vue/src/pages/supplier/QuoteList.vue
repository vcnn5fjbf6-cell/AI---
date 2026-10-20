<template>
  <div class="page-container">
    <PageHero
      eyebrow="Supplier Portal"
      title="供应商报价"
      description="供应商查看可报价清单并提交报价；申请人确认替换商品后，进入经理报价审核。"
    >
      <div class="hero-inline">
        <span class="hero-pill">可处理 {{ total }} 条</span>
        <span class="hero-pill hero-pill--muted">已报价 {{ quotedCount }} 条</span>
      </div>
      <template #aside>
        <div class="hero-stat-panel">
          <p class="hero-stat-kicker">Quote Requests</p>
          <p class="hero-stat-value">{{ total }}</p>
          <p class="hero-stat-note">
            {{
              canViewAllSupplierQuotes
                ? "监督查看模式：可只读查看全部供应商提交。"
                : isSupplierUser
                  ? "仅显示本供应商可处理的报价清单。"
                  : "内部账号可查看报价、提交报价审核并导出审核表。"
            }}
          </p>
        </div>
      </template>
    </PageHero>

    <el-card class="panel-card filter-form page-section" shadow="never">
      <el-form :inline="true" :model="queryModel">
        <el-form-item label="申请单号">
          <el-input v-model="queryModel.reqNo" placeholder="请输入申请单号" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="申请标题">
          <el-input v-model="queryModel.reqTitle" placeholder="请输入申请标题" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="panel-card table-panel page-section" shadow="never">
      <div class="table-toolbar">
        <div>
          <h3 class="card-title">询价请求列表</h3>
          <p class="sub-text">报价中、报价审核中和已审核的采购申请会出现在这里；报价需包含配送/运费，运费不单独结算。</p>
        </div>
      </div>
      <el-alert
        v-if="canViewAllSupplierQuotes"
        class="quote-alert"
        type="info"
        :closable="false"
        show-icon
        title="当前账号已启用全部供应商提交查看权限"
        description="可查看各供应商提交的报价、交期、替换商品和备注；该权限不提供比价审核、导出或修改其他供应商报价的能力。"
      />
      <el-alert
        v-if="supplierBanReason"
        class="quote-alert"
        type="error"
        :closable="false"
        show-icon
        :title="`当前供应商已被封禁，无法报价：${supplierBanReason}`"
      />
      <el-alert
        v-if="supplierSuspendedUntil"
        class="quote-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="`当前供应商因半年内累计 3 次放弃中标，暂停投标至 ${supplierSuspendedUntil}`"
      />

      <el-table v-loading="loading" :data="tableData" stripe>
        <el-table-column prop="reqNo" label="申请单号" min-width="180" />
        <el-table-column prop="reqTitle" label="申请标题" min-width="220" />
        <el-table-column prop="receivingCity" label="收货城市" width="120">
          <template #default="{ row }">{{ row.receivingCity || "-" }}</template>
        </el-table-column>
        <el-table-column label="定向供应商" min-width="170">
          <template #default="{ row }">
            <el-tag v-if="row.directAwardApplied" type="warning" effect="plain">
              指派：{{ row.directAwardSupplierName || "-" }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isSupplierUser" label="需求金额" width="140">
          <template #default="{ row }">{{ formatMoney(row.requestAmount) }}</template>
        </el-table-column>
        <el-table-column label="中标策略" width="130">
          <template #default="{ row }">{{ awardStrategyLabel(row.awardStrategy) }}</template>
        </el-table-column>
        <el-table-column prop="needDate" label="需求日期" width="120" />
        <el-table-column label="报价截止" min-width="190">
          <template #default="{ row }">
            <div class="quote-deadline-cell">
              <el-tag :type="quoteDeadlineTagType(row)" effect="plain">{{ quoteDeadlineLabel(row) }}</el-tag>
              <span>{{ quoteDeadlineSubText(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="自动比价" min-width="170">
          <template #default="{ row }">{{ quoteAutoCompareText(row) }}</template>
        </el-table-column>
        <el-table-column label="报价状态" width="130">
          <template #default="{ row }">
            <el-tag :type="quoteStatusType(row)">{{ quoteStatusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="130">
          <template #default="{ row }">
            <el-tag :type="reviewStatusType(row.leaderConfirmStatus)">
              {{ reviewStatusLabel(row.leaderConfirmStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="我的报价" width="140">
          <template #default="{ row }">{{ row.myQuoteAmount ? formatMoney(row.myQuoteAmount) : "-" }}</template>
        </el-table-column>
        <el-table-column label="我的交期" width="110">
          <template #default="{ row }">{{ row.myLeadTimeDays ? `${row.myLeadTimeDays} 天` : "-" }}</template>
        </el-table-column>
        <el-table-column label="报价时间" min-width="170">
          <template #default="{ row }">{{ row.mySubmittedAt || "-" }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="quote-list-actions">
              <el-button link type="primary" @click="openDetailDialog(row)">查看清单</el-button>
              <el-button v-if="canExtendQuoteDeadline(row)" link type="primary" @click="handleExtendQuoteDeadline(row)">
                延期
              </el-button>
              <el-button v-if="canSubmitQuote(row)" link type="warning" @click="openQuoteDialog(row)">
                {{ row.myQuoteStatus ? "更新报价" : "提交报价" }}
              </el-button>
              <el-button v-if="canSupplierConfirm(row)" link type="success" @click="handleSupplierConfirm(row)">
                确认中标
              </el-button>
              <el-button v-if="canSupplierWaive(row)" link type="danger" @click="handleSupplierWaive(row)">
                放弃中标
              </el-button>
              <el-button v-if="canSupplierExport(row)" link type="primary" @click="handleSupplierExport(row)">
                下载中标清单
              </el-button>
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
    </el-card>

    <el-dialog v-model="detailDialogVisible" title="需求清单" width="92vw">
      <div v-loading="detailLoading">
        <el-descriptions :column="3" border v-if="detailData">
          <el-descriptions-item label="申请单号">{{ detailData.reqNo }}</el-descriptions-item>
          <el-descriptions-item label="申请标题">{{ detailData.reqTitle }}</el-descriptions-item>
          <el-descriptions-item v-if="!isSupplierUser" label="需求金额">{{ formatMoney(detailData.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="需求日期">{{ detailData.needDate || "-" }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ detailData.deptName || "-" }}</el-descriptions-item>
          <el-descriptions-item label="收货城市">{{ detailData.receivingCity || "-" }}</el-descriptions-item>
          <el-descriptions-item label="中标策略">{{ awardStrategyLabel(detailData.awardStrategy) }}</el-descriptions-item>
          <el-descriptions-item label="报价审核">{{ reviewStatusLabel(detailData.leaderConfirmStatus) }}</el-descriptions-item>
          <el-descriptions-item label="自动比价">{{ quoteAutoCompareText(detailData) }}</el-descriptions-item>
          <el-descriptions-item label="报价截止">{{ quoteDeadlineDetailText(detailData) }}</el-descriptions-item>
          <el-descriptions-item v-if="detailData.directAwardApplied" label="定向报价">
            {{ directAwardText(detailData) }}
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          v-if="detailData?.directAwardApplied"
          class="direct-award-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="`低额紧急采购免三方比价，已指派供应商：${detailData.directAwardSupplierName || '-'}`"
          :description="detailData.directAwardReason || '供应商完成有效报价后直接进入报价审核。'"
        />
        <el-alert
          v-if="detailData?.status === 'QUOTING'"
          class="quote-alert"
          :type="quoteDeadlineExpired(detailData) ? 'error' : 'warning'"
          :closable="false"
          show-icon
          :title="quoteDeadlineAlertTitle(detailData)"
          :description="quoteDeadlineAlertDescription(detailData)"
        />

        <el-table :data="detailData?.items || []" stripe class="detail-table">
          <el-table-column prop="lineNo" label="行号" width="70" />
          <el-table-column prop="itemCode" label="物料编码" min-width="130" />
          <el-table-column prop="itemName" label="商品名称" min-width="180" />
          <el-table-column prop="specModel" label="规格型号" min-width="150" />
          <el-table-column prop="receivingCity" label="收货城市" min-width="110">
            <template #default="{ row }">{{ row.receivingCity || detailData?.receivingCity || "-" }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" width="90" />
          <el-table-column prop="unit" label="单位" width="90" />
          <el-table-column prop="purpose" label="采购原因" min-width="200" />
          <el-table-column label="电商链接" min-width="130">
            <template #default="{ row }">
              <el-link v-if="normalizeUrl(row.ecommerceUrl)" :href="normalizeUrl(row.ecommerceUrl)" target="_blank" type="primary">
                打开链接
              </el-link>
              <span v-else-if="row.ecommerceUrl" class="invalid-link-text">链接无效</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="quote-section" v-if="isSupplierUser && myQuote">
          <div class="quote-section-head">
            <div class="quote-section-title">
              <strong>我方报价</strong>
              <el-tag v-if="isAwardWaived(myQuote)" size="small" type="danger">放弃中标</el-tag>
              <span>{{ formatMoney(myQuote.totalAmount) }} / 最长交期 {{ myQuote.leadTimeDays }} 天 / {{ myQuote.submittedAt }}</span>
            </div>
            <div class="quote-section-actions quote-section-actions--compact">
              <el-button v-if="canSupplierConfirmCurrent" size="small" type="success" @click="handleSupplierConfirmFromDetail">
                确认中标
              </el-button>
              <el-button v-if="canSupplierWaiveCurrent" size="small" type="danger" @click="handleSupplierWaiveFromDetail">
                放弃中标
              </el-button>
              <el-button v-if="canSupplierExportCurrent" size="small" @click="handleSupplierExportFromDetail">
                下载中标清单
              </el-button>
            </div>
          </div>
          <el-table :data="myQuoteDisplayItems" stripe>
            <el-table-column prop="lineNo" label="行号" width="70" />
            <el-table-column prop="itemName" label="商品名称" min-width="160" />
            <el-table-column v-if="showMyQuoteDeliveryAddress" label="送达地址" min-width="220">
              <template #default="{ row }">{{ displayDeliveryAddress(row) }}</template>
            </el-table-column>
            <el-table-column prop="qty" label="数量" width="90" />
            <el-table-column label="报价状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.waived ? 'info' : 'success'">{{ row.waived ? "放弃报价" : "已报价" }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="替换商品" width="100">
              <template #default="{ row }">{{ row.replacementFlag ? "是" : "否" }}</template>
            </el-table-column>
            <el-table-column label="成色 / 资产判定" min-width="230">
              <template #default="{ row }">
                <div v-if="!row.waived" class="asset-review-cell">
                  <div class="asset-review-line">
                    <el-tag size="small" effect="plain">{{ conditionLabel(row.originalCondition) }}</el-tag>
                    <el-tag size="small" :type="assetClassificationType(row.originalAssetClassification)">
                      {{ assetClassificationLabel(row.originalAssetClassification) }}
                    </el-tag>
                    <el-tag v-if="row.originalSpecialApprovalRequired" size="small" type="warning">需特殊审批</el-tag>
                    <el-tag v-if="row.conditionMismatch" size="small" type="danger">成色冲突</el-tag>
                  </div>
                  <span>{{ assetCatalogText(row) }}</span>
                  <span v-if="row.replacementFlag">
                    替换：{{ conditionLabel(row.replacementCondition) }} · {{ assetClassificationLabel(row.replacementAssetClassification) }}
                    <el-tag v-if="row.replacementSpecialApprovalRequired" size="small" type="warning">需特殊审批</el-tag>
                  </span>
                </div>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="报价单价" width="130">
              <template #default="{ row }">{{ row.waived ? "-" : formatMoney(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="报价金额" width="130">
              <template #default="{ row }">{{ row.waived ? "-" : formatMoney(row.lineAmount) }}</template>
            </el-table-column>
            <el-table-column prop="leadTimeDays" label="交期(天)" width="100" />
            <el-table-column label="替换/备注" min-width="260">
              <template #default="{ row }">
                <div v-if="row.replacementFlag" class="replacement-detail-cell">
                  <div class="replacement-detail-title">
                    <span>{{ detailText(row.replacementItemName) }}</span>
                    <el-link
                      v-if="hasText(row.replacementEcommerceUrl)"
                      :href="normalizeOptionalUrl(row.replacementEcommerceUrl)"
                      target="_blank"
                      type="primary"
                    >
                      链接
                    </el-link>
                  </div>
                  <div class="replacement-detail-meta">
                    <span>规格：{{ detailText(row.replacementSpecModel) }}</span>
                    <span>替换价：{{ row.replacementUnitPrice == null ? "-" : formatMoney(row.replacementUnitPrice) }}</span>
                    <span>成色：{{ conditionLabel(row.replacementCondition) }}</span>
                  </div>
                  <div v-if="hasText(row.replacementReason)" class="replacement-detail-reason">
                    原因：{{ detailText(row.replacementReason) }}
                  </div>
                  <div v-if="hasText(row.quoteRemark)" class="replacement-detail-remark">
                    备注：{{ detailText(row.quoteRemark) }}
                  </div>
                </div>
                <span v-else class="muted-text">{{ detailText(row.quoteRemark) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-alert
            v-if="isAwardWaived(myQuote)"
            class="quote-waive-reason"
            type="warning"
            :closable="false"
            show-icon
            :title="awardWaiveReason(myQuote)"
          />
        </div>

        <div class="quote-section quote-limited-section" v-if="usesQuoteSummary && quoteSummary && !quoteDetailsVisible">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            :title="`已有 ${submittedQuoteCount} 家供应商提交报价，中标结果产生后可查看供应商报价明细。`"
            description="非管理员在未中标前只能查看报价进度，不能查看供应商名称、报价金额、交期和报价明细。"
          />
          <div class="quote-limited-actions">
            <el-button v-if="canTriggerQuoteReview" size="small" type="primary" @click="handleApplicantSubmitQuoteReview()">
              手动触发比价
            </el-button>
          </div>
        </div>

        <div class="quote-section" v-if="usesQuoteSummary && quoteDetailsVisible && adminQuotes.length > 0">
          <div class="quote-section-head">
            <strong>{{ canViewAllSupplierQuotes ? "全部供应商提交（只读）" : "供应商报价" }}</strong>
            <span>共 {{ adminQuotes.length }} 家供应商</span>
            <div class="quote-section-actions">
              <el-button v-if="canTriggerQuoteReview" size="small" type="primary" @click="handleApplicantSubmitQuoteReview()">
                手动触发比价
              </el-button>
              <el-button v-if="!isSupplierUser" size="small" @click="handleQuoteComparisonExport">导出报价审核表</el-button>
            </div>
          </div>
          <div v-for="quote in adminQuotes" :key="quote.id" class="supplier-quote-block">
            <div class="supplier-quote-title">
              <strong>
                {{ quote.supplierName || "供应商" }}
                <el-tag v-if="quote.selected" size="small" type="success">已选定</el-tag>
                <el-tag v-if="isAwardWaived(quote)" size="small" type="danger">放弃中标</el-tag>
              </strong>
              <span>{{ formatMoney(quote.totalAmount) }} / 最长交期 {{ quote.leadTimeDays }} 天 / {{ quote.submittedAt || "-" }}</span>
              <el-button
                v-if="canApplicantSubmitQuoteReview(quote)"
                size="small"
                type="primary"
                @click="handleApplicantSubmitQuoteReview(quote)"
              >
                {{ detailData?.awardStrategy === "ITEM_LOWEST" ? "按单项最低提交报价审核" : "按总价最低提交报价审核" }}
              </el-button>
            </div>
            <el-table :data="quote.items || []" stripe>
              <el-table-column prop="lineNo" label="行号" width="70" />
              <el-table-column prop="itemCode" label="物料编码" min-width="120" />
              <el-table-column prop="itemName" label="商品名称" min-width="160" />
              <el-table-column v-if="quoteHasDeliveryAddress(quote)" label="送达地址" min-width="220">
                <template #default="{ row }">{{ displayDeliveryAddress(row) }}</template>
              </el-table-column>
              <el-table-column prop="specModel" label="规格型号" min-width="130" />
              <el-table-column prop="qty" label="数量" width="90" />
              <el-table-column label="报价状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.waived ? 'info' : 'success'">{{ row.waived ? "放弃报价" : "已报价" }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="替换商品" width="100">
                <template #default="{ row }">{{ row.replacementFlag ? "是" : "否" }}</template>
              </el-table-column>
              <el-table-column label="成色 / 资产判定" min-width="230">
                <template #default="{ row }">
                  <div v-if="!row.waived" class="asset-review-cell">
                    <div class="asset-review-line">
                      <el-tag size="small" effect="plain">{{ conditionLabel(row.originalCondition) }}</el-tag>
                      <el-tag size="small" :type="assetClassificationType(row.originalAssetClassification)">
                        {{ assetClassificationLabel(row.originalAssetClassification) }}
                      </el-tag>
                      <el-tag v-if="row.originalSpecialApprovalRequired" size="small" type="warning">需特殊审批</el-tag>
                      <el-tag v-if="row.conditionMismatch" size="small" type="danger">成色冲突</el-tag>
                    </div>
                    <span>{{ assetCatalogText(row) }}</span>
                    <span v-if="row.replacementFlag">
                      替换：{{ conditionLabel(row.replacementCondition) }} · {{ assetClassificationLabel(row.replacementAssetClassification) }}
                      <el-tag v-if="row.replacementSpecialApprovalRequired" size="small" type="warning">需特殊审批</el-tag>
                    </span>
                  </div>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="报价单价" width="130">
                <template #default="{ row }">{{ row.waived ? "-" : formatMoney(row.unitPrice) }}</template>
              </el-table-column>
              <el-table-column label="报价金额" width="130">
                <template #default="{ row }">{{ row.waived ? "-" : formatMoney(row.lineAmount) }}</template>
              </el-table-column>
              <el-table-column prop="leadTimeDays" label="交期(天)" width="100" />
              <el-table-column label="替换/备注" min-width="260">
                <template #default="{ row }">
                  <div v-if="row.replacementFlag" class="replacement-detail-cell">
                    <div class="replacement-detail-title">
                      <span>{{ detailText(row.replacementItemName) }}</span>
                      <el-link
                        v-if="hasText(row.replacementEcommerceUrl)"
                        :href="normalizeOptionalUrl(row.replacementEcommerceUrl)"
                        target="_blank"
                        type="primary"
                      >
                        链接
                      </el-link>
                    </div>
                    <div class="replacement-detail-meta">
                      <span>规格：{{ detailText(row.replacementSpecModel) }}</span>
                      <span>替换价：{{ row.replacementUnitPrice == null ? "-" : formatMoney(row.replacementUnitPrice) }}</span>
                      <span>成色：{{ conditionLabel(row.replacementCondition) }}</span>
                    </div>
                    <div v-if="hasText(row.replacementReason)" class="replacement-detail-reason">
                      原因：{{ detailText(row.replacementReason) }}
                    </div>
                    <div v-if="hasText(row.quoteRemark)" class="replacement-detail-remark">
                      备注：{{ detailText(row.quoteRemark) }}
                    </div>
                  </div>
                  <span v-else class="muted-text">{{ detailText(row.quoteRemark) }}</span>
                </template>
              </el-table-column>
            </el-table>
            <el-alert
              v-if="isAwardWaived(quote)"
              class="quote-waive-reason"
              type="warning"
              :closable="false"
              show-icon
              :title="awardWaiveReason(quote)"
            />
          </div>
        </div>

        <el-empty v-if="usesQuoteSummary && quoteDetailsVisible && adminQuotes.length === 0" description="暂无供应商报价" />
      </div>
    </el-dialog>

    <el-dialog v-model="awardDialogVisible" title="中标确认详情" width="900px">
      <div v-loading="awardLoading">
        <el-descriptions :column="2" border v-if="awardQuote && awardTarget">
          <el-descriptions-item label="申请单号">{{ awardTarget.reqNo }}</el-descriptions-item>
          <el-descriptions-item label="申请标题">{{ awardTarget.reqTitle }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ awardQuote.supplierName || "-" }}</el-descriptions-item>
          <el-descriptions-item label="中标金额">{{ formatMoney(awardQuote.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="确认截止">{{ awardQuote.supplierConfirmDueAt || "-" }}</el-descriptions-item>
          <el-descriptions-item label="确认状态">{{ supplierConfirmStatusLabel(awardQuote.supplierConfirmStatus) }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="awardQuote?.items || []" stripe class="detail-table">
          <el-table-column prop="lineNo" label="行号" width="70" />
          <el-table-column prop="itemName" label="中标商品" min-width="170" />
          <el-table-column prop="specModel" label="规格型号" min-width="130" />
          <el-table-column prop="qty" label="数量" width="90" />
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column label="中标单价" width="120">
            <template #default="{ row }">{{ formatMoney(displayAwardUnitPrice(row)) }}</template>
          </el-table-column>
          <el-table-column label="中标金额" width="120">
            <template #default="{ row }">{{ formatMoney(displayAwardLineAmount(row)) }}</template>
          </el-table-column>
          <el-table-column prop="leadTimeDays" label="交期(天)" width="100" />
          <el-table-column label="成色 / 资产判定" min-width="210">
            <template #default="{ row }">
              <div class="asset-review-cell">
                <div class="asset-review-line">
                  <el-tag size="small" effect="plain">
                    {{ conditionLabel(row.replacementFlag && row.replacementReviewStatus === "APPROVED" ? row.replacementCondition : row.originalCondition) }}
                  </el-tag>
                  <el-tag
                    size="small"
                    :type="assetClassificationType(row.replacementFlag && row.replacementReviewStatus === 'APPROVED' ? row.replacementAssetClassification : row.originalAssetClassification)"
                  >
                    {{ assetClassificationLabel(row.replacementFlag && row.replacementReviewStatus === "APPROVED" ? row.replacementAssetClassification : row.originalAssetClassification) }}
                  </el-tag>
                  <el-tag v-if="awardSpecialApprovalRequired(row)" size="small" type="warning">需特殊审批</el-tag>
                </div>
                <span>{{ assetCatalogText(row, Boolean(row.replacementFlag && row.replacementReviewStatus === "APPROVED")) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="送达地址" min-width="180">
            <template #default="{ row }">{{ row.deliveryAddress || "-" }}</template>
          </el-table-column>
          <el-table-column label="替换商品" min-width="180">
            <template #default="{ row }">
              <span v-if="row.replacementFlag && row.replacementReviewStatus === 'APPROVED'">
                {{ row.replacementItemName || "-" }} / {{ row.replacementSpecModel || "-" }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
        <el-input
          v-model="awardWaiveRemark"
          class="award-remark-input"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          placeholder="放弃中标时填写原因；确认中标无需填写"
        />
      </div>
      <template #footer>
        <el-button @click="awardDialogVisible = false">关闭</el-button>
        <el-button :disabled="!awardDialogCanExport" @click="handleAwardDialogExport">下载中标清单</el-button>
        <el-button
          v-if="awardDialogCanWaive"
          type="danger"
          :disabled="!awardTarget"
          :loading="awardActionLoading"
          @click="handleAwardDialogWaive"
        >
          放弃中标
        </el-button>
        <el-button
          v-if="awardDialogCanConfirm"
          type="success"
          :disabled="!awardTarget"
          :loading="awardActionLoading"
          @click="handleAwardDialogConfirm"
        >
          确认中标
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="quoteDialogVisible" title="逐项报价" width="96vw">
      <div v-loading="quoteLoading">
        <el-alert type="info" :closable="false" show-icon class="quote-alert">
          <div class="quote-alert-content">
            <span>申请单：{{ quoteTarget.reqNo || "-" }} / {{ quoteTarget.reqTitle || "-" }} / 收货城市：{{ currentReceivingCity || "-" }}</span>
            <span>{{ freightIncludedNotice }}</span>
          </div>
        </el-alert>
        <el-alert
          v-if="quoteTarget.id"
          class="quote-alert"
          :type="quoteDeadlineExpired(quoteTarget) ? 'error' : 'warning'"
          :closable="false"
          show-icon
          :title="quoteDeadlineAlertTitle(quoteTarget)"
          :description="quoteDeadlineAlertDescription(quoteTarget)"
        />

        <div class="quote-toolbar">
          <div>
            <strong>报价总额：{{ formatMoney(quoteTotalAmount) }}</strong>
            <span class="quote-total-note">按原商品报价计算 · 最长交期 {{ maxLeadTimeDays }} 天</span>
          </div>
          <div class="quote-toolbar-actions">
            <el-button @click="exportQuoteTemplate">导出报价模板</el-button>
            <el-button :loading="quoteImportLoading" @click="openImportDialog">批量导入报价</el-button>
            <input ref="quoteImportFileInput" class="hidden-file-input" type="file" accept=".xlsx,.xls" @change="handleQuoteImportFileChange" />
          </div>
        </div>

        <el-table ref="quoteTableRef" :data="quoteLines" row-key="purchaseRequestItemId" stripe class="quote-table">
          <el-table-column type="expand" width="44">
            <template #default="{ row }">
              <div v-if="row.replacementFlag && !row.waived" class="replacement-edit-panel">
                <div class="replacement-edit-head">
                  <strong>第 {{ row.lineNo }} 行替换方案</strong>
                  <span>原商品报价仍保留，申请人后续可在原商品与替换商品之间确认。</span>
                </div>
                <el-row :gutter="12">
                  <el-col :span="5">
                    <div class="field-label">替换商品名称 <span class="required-label">必填</span></div>
                    <el-input
                      v-model="row.replacementItemName"
                      class="replacement-name-input"
                      :class="{ 'is-replacement-invalid': Boolean(replacementFieldError(row, 'name')) }"
                      :data-item-id="row.purchaseRequestItemId"
                      placeholder="填写替换后的商品名称"
                      @input="clearReplacementFieldError(row, 'name')"
                    />
                    <div v-if="replacementFieldError(row, 'name')" class="replacement-field-error">
                      {{ replacementFieldError(row, "name") }}
                    </div>
                  </el-col>
                  <el-col :span="4">
                    <div class="field-label">替换规格型号 <span class="required-label">必填</span></div>
                    <el-input
                      v-model="row.replacementSpecModel"
                      class="replacement-spec-input"
                      :class="{ 'is-replacement-invalid': Boolean(replacementFieldError(row, 'spec')) }"
                      :data-item-id="row.purchaseRequestItemId"
                      placeholder="填写规格/型号"
                      @input="clearReplacementFieldError(row, 'spec')"
                    />
                    <div v-if="replacementFieldError(row, 'spec')" class="replacement-field-error">
                      {{ replacementFieldError(row, "spec") }}
                    </div>
                  </el-col>
                  <el-col :span="4">
                    <div class="field-label">替换商品成色</div>
                    <el-select v-model="row.replacementCondition" class="condition-select">
                      <el-option v-for="option in productConditionOptions" :key="option.value" :label="option.label" :value="option.value" />
                    </el-select>
                  </el-col>
                  <el-col :span="4">
                    <div class="field-label">替换商品单价 <span class="required-label">必填</span></div>
                    <el-input-number
                      v-model="row.replacementUnitPrice"
                      :min="0.01"
                      :precision="2"
                      :controls="false"
                      class="quote-input replacement-price-input"
                      :class="{ 'is-replacement-invalid': Boolean(replacementFieldError(row, 'price')) }"
                      :data-item-id="row.purchaseRequestItemId"
                      @change="clearReplacementFieldError(row, 'price')"
                    />
                    <div v-if="replacementFieldError(row, 'price')" class="replacement-field-error">
                      {{ replacementFieldError(row, "price") }}
                    </div>
                  </el-col>
                  <el-col :span="7">
                    <div class="field-label">
                      {{ replacementLinkFieldLabel }} <span class="required-label">必填</span>
                    </div>
                    <el-input
                      v-model="row.replacementEcommerceUrl"
                      class="replacement-url-input"
                      :class="{ 'is-replacement-invalid': Boolean(replacementFieldError(row, 'url')) }"
                      :data-item-id="row.purchaseRequestItemId"
                      :placeholder="replacementLinkPlaceholder"
                      @input="clearReplacementFieldError(row, 'url')"
                      @blur="normalizeReplacementLink(row)"
                    />
                    <div v-if="replacementFieldError(row, 'url')" class="replacement-field-error">
                      {{ replacementFieldError(row, "url") }}
                    </div>
                  </el-col>
                </el-row>
                <el-row :gutter="12" class="replacement-extra-row">
                  <el-col :span="12">
                    <div class="field-label">替换原因 <span class="required-label">必填</span></div>
                    <el-input
                      v-model="row.replacementReason"
                      class="replacement-reason-input"
                      :class="{ 'is-replacement-invalid': Boolean(replacementFieldError(row, 'reason')) }"
                      :data-item-id="row.purchaseRequestItemId"
                      placeholder="例如原型号缺货、同等规格替代等"
                      @input="clearReplacementFieldError(row, 'reason')"
                    />
                    <div v-if="replacementFieldError(row, 'reason')" class="replacement-field-error">
                      {{ replacementFieldError(row, "reason") }}
                    </div>
                  </el-col>
                  <el-col :span="12">
                    <div class="field-label">二手商品说明</div>
                    <el-input
                      v-model="row.replacementSecondHandRemark"
                      :disabled="row.replacementCondition !== 'USED'"
                      :placeholder="row.replacementCondition === 'USED' ? '必填：来源、使用年限、翻新或拆机情况' : '仅二手商品填写'"
                    />
                  </el-col>
                </el-row>
                <div class="replacement-compare-preview">
                  <div class="replacement-compare-card">
                    <span>原商品</span>
                    <strong>{{ row.itemName }}</strong>
                    <small>规格：{{ row.specModel || "-" }} · 报价：{{ formatMoney(row.unitPrice) }}</small>
                  </div>
                  <div class="replacement-compare-arrow">→</div>
                  <div class="replacement-compare-card replacement-compare-card--target">
                    <span>替换商品</span>
                    <strong>{{ row.replacementItemName || "待填写" }}</strong>
                    <small>规格：{{ row.replacementSpecModel || "待填写" }} · 报价：{{ formatMoney(row.replacementUnitPrice) }}</small>
                    <el-link
                      v-if="normalizeOptionalUrl(row.replacementEcommerceUrl)"
                      :href="normalizeOptionalUrl(row.replacementEcommerceUrl)"
                      target="_blank"
                      type="primary"
                    >
                      查看商品链接
                    </el-link>
                  </div>
                  <el-tag :type="replacementPriceDiffType(row)" effect="plain">
                    {{ replacementPriceDiffText(row) }}
                  </el-tag>
                </div>
                <el-tag :type="replacementQuoteLineRuleType(row)" effect="plain" class="replacement-rule-tag">
                  {{ replacementQuoteLineRuleLabel(row) }}
                </el-tag>
              </div>
              <span v-else class="muted-text">未标记替换商品</span>
            </template>
          </el-table-column>
          <el-table-column prop="lineNo" label="行号" width="64" />
          <el-table-column prop="itemCode" label="物料编码" min-width="120" />
          <el-table-column prop="itemName" label="商品名称" min-width="150" />
          <el-table-column prop="specModel" label="规格型号" min-width="130" />
          <el-table-column prop="receivingCity" label="收货城市" min-width="110">
            <template #default="{ row }">{{ row.receivingCity || currentReceivingCity || "-" }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" width="90" />
          <el-table-column prop="unit" label="单位" width="70" />
          <el-table-column label="原商品成色" min-width="210">
            <template #default="{ row }">
              <div class="condition-editor">
                <el-select v-model="row.originalCondition" :disabled="row.waived" class="condition-select">
                  <el-option v-for="option in productConditionOptions" :key="option.value" :label="option.label" :value="option.value" />
                </el-select>
                <el-input
                  v-if="row.originalCondition === 'USED'"
                  v-model="row.originalSecondHandRemark"
                  :disabled="row.waived"
                  placeholder="必填：来源、使用年限、翻新或拆机情况"
                />
                <el-tag v-if="!row.waived" :type="quoteLineRuleType(row)" effect="plain">
                  {{ quoteLineRuleLabel(row) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="替换商品" width="110">
            <template #default="{ row }">
              <el-switch
                v-model="row.replacementFlag"
                :disabled="row.waived"
                active-text="是"
                inactive-text="否"
                inline-prompt
                @change="handleReplacementFlagChange(row)"
              />
            </template>
          </el-table-column>
          <el-table-column label="报价状态" width="120">
            <template #default="{ row }">
              <el-button link :type="row.waived ? 'primary' : 'warning'" @click="toggleWaived(row)">
                {{ row.waived ? "恢复报价" : "放弃单项" }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="原商品报价单价" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.unitPrice" :disabled="row.waived" :min="0.01" :precision="2" :controls="false" class="quote-input" />
            </template>
          </el-table-column>
          <el-table-column label="原商品报价金额" width="130">
            <template #default="{ row }">{{ row.waived ? "-" : formatMoney(lineAmount(row)) }}</template>
          </el-table-column>
          <el-table-column label="交期(天)" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.leadTimeDays" :disabled="row.waived" :min="1" :max="365" :controls="false" class="quote-input" />
            </template>
          </el-table-column>
          <el-table-column label="报价备注" min-width="180">
            <template #default="{ row }">
              <el-input v-model="row.quoteRemark" placeholder="填写品牌、型号或含税说明" />
            </template>
          </el-table-column>
        </el-table>

        <el-form :model="quoteForm" label-width="90px" class="quote-form">
          <el-form-item label="总备注">
            <el-input v-model="quoteForm.quoteRemark" type="textarea" :rows="3" placeholder="报价整体说明，可不填" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="quoteDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="quoteDeadlineExpired(quoteTarget)" :loading="quoteSubmitting" @click="handleSubmitQuote">提交报价</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import PageHero from "../../components/PageHero.vue";
import { confirmWinningQuoteApi, exportQuoteComparisonApi, extendQuoteDeadlineApi, getQuoteSummaryApi } from "../../api/workflow";
import {
  ecommerceUrlHref,
  normalizeSupplierProductLinkInput,
  XFS_INTERNAL_PRODUCT_CODE_RULE
} from "../../utils/ecommerce-url";
import {
  confirmAwardApi,
  exportAwardApi,
  exportQuoteTemplateApi,
  getAwardConfirmationDetailApi,
  getMyQuoteApi,
  getQuoteRequestDetailApi,
  getQuoteRequestsPageApi,
  importQuotePreviewApi,
  submitQuoteApi,
  waiveAwardApi
} from "../../api/supplier";
import { useAuthStore } from "../../stores/auth";
import type { PurchaseRequestItem, PurchaseRequestVO } from "../../types/purchase";
import type { SupplierQuoteItemVO, SupplierQuoteRequestVO, SupplierQuoteSummaryVO, SupplierQuoteVO } from "../../types/supplier";
import { hasPermission } from "../../utils/permission";

type QuoteDeadlineSource = {
  id?: number;
  status?: string;
  quoteCountdownStartedAt?: string | null;
  quoteCountdownDueAt?: string | null;
};

interface QuoteLineForm {
  purchaseRequestItemId: number;
  lineNo: number;
  itemCode: string;
  itemName: string;
  specModel: string;
  reqType: string;
  receivingCity: string;
  qty: number;
  unit: string;
  purpose: string;
  ecommerceUrl: string;
  unitPrice: number | null;
  leadTimeDays: number | null;
  originalCondition: "NEW" | "USED";
  originalSecondHandRemark: string;
  replacementFlag: boolean;
  replacementItemName: string;
  replacementSpecModel: string;
  replacementEcommerceUrl: string;
  replacementUnitPrice: number | null;
  replacementReason: string;
  replacementCondition: "NEW" | "USED";
  replacementSecondHandRemark: string;
  waived: boolean;
  quoteRemark: string;
}

type ReplacementValidationField = "name" | "spec" | "price" | "url" | "reason";

type ReplacementValidationErrors = Partial<Record<ReplacementValidationField, string>>;

type QuoteTableRef = {
  toggleRowExpansion: (row: QuoteLineForm, expanded?: boolean) => void;
};

interface ValidImportQuoteRow {
  target: QuoteLineForm;
  replacementFlag: boolean;
  replacementItemName: string;
  replacementSpecModel: string;
  replacementEcommerceUrl: string;
  replacementUnitPrice: number | null;
  replacementReason: string;
  originalCondition: "NEW" | "USED";
  originalSecondHandRemark: string;
  replacementCondition: "NEW" | "USED";
  replacementSecondHandRemark: string;
  waived: boolean;
  unitPrice: number | null;
  leadTimeDays: number | null;
  quoteRemark: string;
}

const authStore = useAuthStore();
const route = useRoute();
const loading = ref(false);
const detailLoading = ref(false);
const quoteLoading = ref(false);
const quoteSubmitting = ref(false);
const quoteImportLoading = ref(false);
const total = ref(0);
const tableData = ref<SupplierQuoteRequestVO[]>([]);
const detailData = ref<PurchaseRequestVO | null>(null);
const myQuote = ref<SupplierQuoteVO | null>(null);
const quoteSummary = ref<SupplierQuoteSummaryVO | null>(null);
const detailDialogVisible = ref(false);
const quoteDialogVisible = ref(false);
const awardDialogVisible = ref(false);
const awardLoading = ref(false);
const awardActionLoading = ref(false);
const awardTarget = ref<SupplierQuoteRequestVO | null>(null);
const awardQuote = ref<SupplierQuoteVO | null>(null);
const awardWaiveRemark = ref("");
const quoteImportFileInput = ref<HTMLInputElement>();
const quoteTableRef = ref<QuoteTableRef>();
const quoteLines = ref<QuoteLineForm[]>([]);
const replacementValidationErrors = reactive<Record<number, ReplacementValidationErrors>>({});
const countdownNow = ref(Date.now());
let countdownTimer: ReturnType<typeof window.setInterval> | null = null;

const queryModel = reactive({
  pageNo: 1,
  pageSize: 10,
  reqNo: "",
  reqTitle: ""
});

const quoteTarget = reactive<Partial<SupplierQuoteRequestVO>>({});

const quoteForm = reactive({
  purchaseRequestId: 0,
  quoteRemark: ""
});

const canViewAllSupplierQuotes = computed(() => hasPermission("supplier_quote:view-all"));
const isSupplierSideAccount = computed(() => {
  const userType = authStore.user?.userType?.trim().toUpperCase();
  return userType === "SUPPLIER"
    || userType === "SUPPLIER_QUOTE_VIEWER"
    || authStore.effectiveRoleCodes.some((role) => ["SUPPLIER", "SUPPLIER_QUOTE_VIEWER"].includes(role.toUpperCase()))
    || authStore.user?.supplierId != null;
});
const quotedCount = computed(() => tableData.value.filter((row) => Boolean(row.myQuoteStatus)).length);
const isSupplierUser = computed(() => isSupplierSideAccount.value && !canViewAllSupplierQuotes.value);
const usesQuoteSummary = computed(() => !isSupplierUser.value || canViewAllSupplierQuotes.value);
const SYSTEM_ADMIN_ROLES = ["SUPER_ADMIN", "SYS_ADMIN", "ADMIN"];
const isSystemAdmin = computed(() => authStore.hasAnyRole(SYSTEM_ADMIN_ROLES));
const adminQuotes = computed(() => quoteSummary.value?.quotes || []);
const quoteDetailsVisible = computed(() => quoteSummary.value?.quoteDetailsVisible !== false);
const submittedQuoteCount = computed(() => quoteSummary.value?.supplierQuoteCount ?? adminQuotes.value.length);
const supplierBannedRow = computed(() => tableData.value.find((row) => row.supplierBanned));
const supplierBanReason = computed(() => (supplierBannedRow.value ? supplierBannedRow.value.supplierBanReason || "请联系采购管理员" : ""));
const supplierSuspendedUntil = computed(() => tableData.value.find((row) => row.supplierBiddingSuspended)?.supplierBidSuspendedUntil || "");
const supplierBanNoticeShownFor = ref("");
const quoteTotalAmount = computed(() => quoteLines.value.reduce((sum, line) => sum + lineAmount(line), 0));
const myQuoteDisplayItems = computed(() => {
  const items = myQuote.value?.items || [];
  return myQuote.value?.selected && detailData.value?.leaderConfirmStatus === "CONFIRMED"
    ? items.filter((item) => item.awarded)
    : items;
});
const showMyQuoteDeliveryAddress = computed(() => quoteHasDeliveryAddress(myQuote.value));
const maxLeadTimeDays = computed(() =>
  quoteLines.value
    .filter((line) => !line.waived)
    .reduce((max, line) => Math.max(max, Number(line.leadTimeDays || 0)), 0)
);
const freightIncludedNotice = "报价需包含配送/运费，运费不单独结算；请按收货城市将配送成本摊入商品单价。";
const productConditionOptions = [
  { label: "全新", value: "NEW" },
  { label: "二手", value: "USED" }
] as const;
const currentReceivingCity = computed(() => quoteTarget.receivingCity || detailData.value?.receivingCity || "");
const replacementLinkFieldLabel = computed(() => quoteTarget.internalProductCodeRule === XFS_INTERNAL_PRODUCT_CODE_RULE
  ? "替换商品链接 / 鑫方盛内部编号"
  : "替换商品链接");
const replacementLinkPlaceholder = computed(() => quoteTarget.internalProductCodeRule === XFS_INTERNAL_PRODUCT_CODE_RULE
  ? "填写商品详情页链接或鑫方盛内部编号"
  : "填写京东/淘宝/厂商商品页链接");
const canTriggerQuoteReview = computed(() => {
  if (!detailData.value || isSupplierSideAccount.value || detailData.value.status !== "QUOTING") {
    return false;
  }
  const requiredQuoteCount = detailData.value.directAwardApplied ? 1 : 3;
  return (isSystemAdmin.value || detailData.value.applicantUserId === authStore.user?.userId)
    && submittedQuoteCount.value >= requiredQuoteCount;
});
const canSupplierConfirmCurrent = computed(() => {
  return Boolean(
    detailData.value &&
    myQuote.value?.selected &&
    detailData.value.leaderConfirmStatus === "CONFIRMED" &&
    myQuote.value.supplierConfirmStatus === "PENDING"
  );
});
const canSupplierWaiveCurrent = computed(() => {
  return Boolean(
    detailData.value &&
    myQuote.value?.selected &&
    detailData.value.leaderConfirmStatus === "CONFIRMED" &&
    myQuote.value.supplierConfirmStatus === "PENDING"
  );
});
const canSupplierExportCurrent = computed(() => {
  return Boolean(
    detailData.value &&
    myQuote.value?.selected &&
    detailData.value.leaderConfirmStatus === "CONFIRMED" &&
    myQuote.value.supplierConfirmStatus === "CONFIRMED"
  );
});
const awardDialogCanConfirm = computed(() => awardQuote.value?.supplierConfirmStatus === "PENDING");
const awardDialogCanWaive = computed(() => awardQuote.value?.supplierConfirmStatus === "PENDING");
const awardDialogCanExport = computed(() => {
  return Boolean(awardTarget.value && awardQuote.value?.selected && awardQuote.value.supplierConfirmStatus === "CONFIRMED");
});

async function loadTableData(): Promise<void> {
  loading.value = true;
  try {
    const data = await getQuoteRequestsPageApi({
      pageNo: queryModel.pageNo,
      pageSize: queryModel.pageSize,
      reqNo: queryModel.reqNo || undefined,
      reqTitle: queryModel.reqTitle || undefined
    });
    tableData.value = data.records;
    total.value = data.total;
    showSupplierBanDialogIfNeeded();
  } finally {
    loading.value = false;
  }
}

function showSupplierBanDialogIfNeeded(): void {
  if (!isSupplierUser.value || !supplierBanReason.value || supplierBanNoticeShownFor.value === supplierBanReason.value) {
    return;
  }
  supplierBanNoticeShownFor.value = supplierBanReason.value;
  void ElMessageBox.alert(
    `当前供应商已被封禁，无法提交报价，也不会参与自动定标和递补。封禁原因：${supplierBanReason.value}`,
    "供应商已封禁",
    { type: "error", confirmButtonText: "我知道了" }
  );
}

function handleSearch(): void {
  queryModel.pageNo = 1;
  loadTableData();
}

function handleReset(): void {
  queryModel.reqNo = "";
  queryModel.reqTitle = "";
  queryModel.pageNo = 1;
  loadTableData();
}

function handlePageChange(pageNo: number): void {
  queryModel.pageNo = pageNo;
  loadTableData();
}

function handleSizeChange(pageSize: number): void {
  queryModel.pageSize = pageSize;
  queryModel.pageNo = 1;
  loadTableData();
}

async function openDetailDialog(row: SupplierQuoteRequestVO): Promise<void> {
  detailDialogVisible.value = true;
  detailLoading.value = true;
  myQuote.value = null;
  quoteSummary.value = null;
  try {
    const [detail, ownQuote, summary] = await Promise.all([
      getQuoteRequestDetailApi(row.id),
      isSupplierUser.value ? getMyQuoteApi(row.id) : Promise.resolve(null),
      usesQuoteSummary.value ? getQuoteSummaryApi(row.id) : Promise.resolve(null)
    ]);
    detailData.value = detail;
    myQuote.value = ownQuote;
    quoteSummary.value = summary;
  } finally {
    detailLoading.value = false;
  }
}

async function openQuoteDialog(row: SupplierQuoteRequestVO): Promise<void> {
  Object.assign(quoteTarget, row);
  quoteForm.purchaseRequestId = row.id;
  quoteForm.quoteRemark = "";
  quoteLines.value = [];
  quoteDialogVisible.value = true;
  quoteLoading.value = true;
  try {
    const [detail, quote] = await Promise.all([getQuoteRequestDetailApi(row.id), getMyQuoteApi(row.id)]);
    detailData.value = detail;
    myQuote.value = quote;
    Object.assign(quoteTarget, {
      status: detail.status,
      quoteCountdownStartedAt: detail.quoteCountdownStartedAt,
      quoteCountdownDueAt: detail.quoteCountdownDueAt
    });
    quoteForm.quoteRemark = quote?.quoteRemark || "";
    quoteLines.value = buildQuoteLines(detail.items || [], quote?.items || []);
  } finally {
    quoteLoading.value = false;
  }
}

function buildQuoteLines(items: PurchaseRequestItem[], existingItems: SupplierQuoteItemVO[]): QuoteLineForm[] {
  const existingMap = new Map(existingItems.map((item) => [item.purchaseRequestItemId, item]));
  return items
    .filter((item) => Boolean(item.id))
    .map((item) => {
      const existing = existingMap.get(item.id!);
      return {
        purchaseRequestItemId: item.id!,
        lineNo: item.lineNo || 0,
        itemCode: item.itemCode || "",
        itemName: item.itemName || "",
        specModel: item.specModel || "",
        reqType: item.reqType || "",
        receivingCity: item.receivingCity || "",
        qty: Number(item.qty || 0),
        unit: item.unit || "",
        purpose: item.purpose || "",
        ecommerceUrl: item.ecommerceUrl || "",
        unitPrice: existing?.waived ? null : Number(existing?.unitPrice ?? item.unitPrice ?? 0),
        leadTimeDays: existing?.waived ? null : Number(existing?.leadTimeDays ?? 7),
        originalCondition: existing?.originalCondition === "USED" ? "USED" : "NEW",
        originalSecondHandRemark: existing?.originalSecondHandRemark || "",
        replacementFlag: Boolean(existing?.replacementFlag),
        replacementItemName: existing?.replacementItemName || "",
        replacementSpecModel: existing?.replacementSpecModel || "",
        replacementEcommerceUrl: existing?.replacementEcommerceUrl || "",
        replacementUnitPrice: existing?.replacementUnitPrice == null ? null : Number(existing.replacementUnitPrice),
        replacementReason: existing?.replacementReason || "",
        replacementCondition: existing?.replacementCondition === "USED" ? "USED" : "NEW",
        replacementSecondHandRemark: existing?.replacementSecondHandRemark || "",
        waived: Boolean(existing?.waived),
        quoteRemark: existing?.quoteRemark || ""
      };
    });
}

async function handleSubmitQuote(): Promise<void> {
  if (quoteDeadlineExpired(quoteTarget)) {
    ElMessage.warning("报价截止时间已到，未提交报价按放弃报价处理");
    return;
  }
  if (!quoteForm.purchaseRequestId || quoteLines.value.length === 0) {
    ElMessage.warning("没有可报价的采购明细");
    return;
  }
  const activeLines = quoteLines.value.filter((line) => !line.waived);
  const invalidLine = activeLines.find((line) => Number(line.unitPrice) <= 0 || Number(line.leadTimeDays) <= 0);
  if (invalidLine) {
    ElMessage.warning(`第 ${invalidLine.lineNo} 行请填写有效的报价单价和交期`);
    return;
  }
  const missingConditionLine = activeLines.find((line) => !line.originalCondition);
  if (missingConditionLine) {
    ElMessage.warning(`第 ${missingConditionLine.lineNo} 行请选择原商品成色`);
    return;
  }
  const missingUsedRemarkLine = activeLines.find(
    (line) => line.originalCondition === "USED" && !line.originalSecondHandRemark.trim()
  );
  if (missingUsedRemarkLine) {
    ElMessage.warning(`第 ${missingUsedRemarkLine.lineNo} 行原商品为二手，请填写二手商品说明`);
    return;
  }
  clearAllReplacementErrors();
  const invalidReplacementLine = activeLines.find((line) => {
    if (!line.replacementFlag) {
      return false;
    }
    const errors: ReplacementValidationErrors = {};
    if (!line.replacementItemName.trim()) {
      errors.name = "请填写替换商品名称";
    }
    if (!line.replacementSpecModel.trim()) {
      errors.spec = "请填写替换规格型号";
    }
    if (Number(line.replacementUnitPrice) <= 0) {
      errors.price = "请填写大于 0 的替换商品单价";
    }
    const normalizedUrl = normalizeSupplierProductLinkInput(
      line.replacementEcommerceUrl,
      quoteTarget.internalProductCodeRule
    );
    if (!line.replacementEcommerceUrl.trim()) {
      errors.url = "请填写替换商品链接";
    } else if (normalizedUrl.error) {
      errors.url = normalizedUrl.error;
    } else {
      line.replacementEcommerceUrl = normalizedUrl.value;
    }
    if (!line.replacementReason.trim()) {
      errors.reason = "请填写替换原因";
    }
    if (Object.keys(errors).length === 0) {
      return false;
    }
    replacementValidationErrors[line.purchaseRequestItemId] = errors;
    return true;
  });
  if (invalidReplacementLine) {
    await revealReplacementLine(invalidReplacementLine, true);
    ElMessage.warning(`第 ${invalidReplacementLine.lineNo} 行替换方案尚未填写完整，已为你展开并定位`);
    return;
  }
  const invalidReplacementConditionLine = activeLines.find(
    (line) => line.replacementFlag && (!line.replacementCondition
      || (line.replacementCondition === "USED" && !line.replacementSecondHandRemark.trim()))
  );
  if (invalidReplacementConditionLine) {
    ElMessage.warning(`第 ${invalidReplacementConditionLine.lineNo} 行请补充替换商品成色；二手商品还需填写说明`);
    return;
  }

  const replacementLines = activeLines.filter((line) => line.replacementFlag);
  if (replacementLines.length > 0) {
    try {
      await ElMessageBox.confirm(
        `本次报价包含 ${replacementLines.length} 条替换方案。系统会保留原商品报价，提交后进行 AI 复核，并在中标后交由申请人确认。`,
        "确认提交替换方案",
        {
          type: "warning",
          confirmButtonText: "确认提交",
          cancelButtonText: "返回检查"
        }
      );
    } catch {
      return;
    }
  }

  quoteSubmitting.value = true;
  try {
    await submitQuoteApi({
      purchaseRequestId: quoteForm.purchaseRequestId,
      totalAmount: quoteTotalAmount.value,
      leadTimeDays: maxLeadTimeDays.value,
      quoteRemark: quoteForm.quoteRemark || null,
      items: quoteLines.value.map((line) => ({
        purchaseRequestItemId: line.purchaseRequestItemId,
        unitPrice: line.waived ? null : Number(line.unitPrice),
        leadTimeDays: line.waived ? null : Number(line.leadTimeDays),
        originalCondition: line.waived ? null : line.originalCondition,
        originalSecondHandRemark: line.waived || line.originalCondition !== "USED" ? null : line.originalSecondHandRemark.trim(),
        replacementFlag: line.waived ? false : line.replacementFlag,
        replacementItemName: line.waived || !line.replacementFlag ? null : line.replacementItemName.trim(),
        replacementSpecModel: line.waived || !line.replacementFlag ? null : line.replacementSpecModel.trim() || null,
        replacementEcommerceUrl: line.waived || !line.replacementFlag ? null : line.replacementEcommerceUrl.trim() || null,
        replacementUnitPrice: line.waived || !line.replacementFlag ? null : Number(line.replacementUnitPrice),
        replacementReason: line.waived || !line.replacementFlag ? null : line.replacementReason.trim() || null,
        replacementCondition: line.waived || !line.replacementFlag ? null : line.replacementCondition,
        replacementSecondHandRemark: line.waived || !line.replacementFlag || line.replacementCondition !== "USED"
          ? null
          : line.replacementSecondHandRemark.trim(),
        waived: line.waived,
        quoteRemark: line.quoteRemark || null
      }))
    });
    ElMessage.success("报价提交成功");
    quoteDialogVisible.value = false;
    await loadTableData();
  } finally {
    quoteSubmitting.value = false;
  }
}

function canApplicantSubmitQuoteReview(quote?: SupplierQuoteVO): boolean {
  if (!detailData.value || isSupplierSideAccount.value || detailData.value.status !== "QUOTING") {
    return false;
  }
  const requiredQuoteCount = detailData.value.directAwardApplied ? 1 : 3;
  return (!quote || quoteDetailsVisible.value)
    && submittedQuoteCount.value >= requiredQuoteCount
    && (isSystemAdmin.value || detailData.value.applicantUserId === authStore.user?.userId);
}

async function handleApplicantSubmitQuoteReview(quote?: SupplierQuoteVO): Promise<void> {
  if (!detailData.value) {
    return;
  }
  const requiredQuoteCount = detailData.value.directAwardApplied ? 1 : 3;
  if (submittedQuoteCount.value < requiredQuoteCount) {
    ElMessage.warning(detailData.value.directAwardApplied ? "定向供应商完成报价后才能提交报价审核" : "至少需要 3 家供应商报价后才能比价");
    return;
  }
  const result = await ElMessageBox.prompt(
    detailData.value.directAwardApplied
      ? "该紧急采购低于后台阈值，已定向给历史单项中标次数最多的供应商；确认报价内容符合需求后提交经理审核。"
      : quoteDetailsVisible.value
        ? "确认供应商报价中的替换商品、规格型号和备注符合需求后，将按当前中标策略自动选出最低价并提交后续审核。"
        : "系统将按当前中标策略自动比价并生成中标结果，非管理员在中标前不会看到供应商报价明细。",
    detailData.value.directAwardApplied ? "提交定向报价审核" : "手动触发比价",
    {
      confirmButtonText: detailData.value.directAwardApplied ? "提交审核" : "开始比价",
      cancelButtonText: "取消",
      inputType: "textarea",
      inputPlaceholder: "填写比价或替换商品确认意见，可不填"
    }
  ).catch(() => null);
  if (!result) {
    return;
  }
  quoteSummary.value = await confirmWinningQuoteApi(detailData.value.id, {
    quoteId: quote?.id,
    awardStrategy: detailData.value.awardStrategy || "TOTAL_LOWEST",
    leaderConfirmRemark: result.value || null
  });
  ElMessage.success("已完成比价并提交经理报价审核");
  await loadTableData();
  detailData.value = await getQuoteRequestDetailApi(detailData.value.id);
}

function canExtendQuoteDeadline(row?: QuoteDeadlineSource | null): boolean {
  return Boolean(!isSupplierUser.value && isSystemAdmin.value && row?.id && row.status === "QUOTING");
}

async function handleExtendQuoteDeadline(row?: QuoteDeadlineSource | null): Promise<void> {
  if (!row?.id) {
    return;
  }
  const result = await ElMessageBox.prompt(
    "填写要增加的自然日天数。延期后供应商可在新的截止时间前提交或更新报价。",
    "延长报价时间",
    {
      confirmButtonText: "确认延期",
      cancelButtonText: "取消",
      inputValue: "1",
      inputPattern: /^(?:[1-9]|[1-5]\d|60)$/,
      inputErrorMessage: "请输入 1 到 60 的整数"
    }
  ).catch(() => null);
  if (!result) {
    return;
  }
  const days = Number(result.value);
  const updated = await extendQuoteDeadlineApi(row.id, { days });
  ElMessage.success(`已延长 ${days} 个自然日`);
  if (detailData.value?.id === updated.id) {
    detailData.value = updated;
  }
  if (quoteTarget.id === updated.id) {
    Object.assign(quoteTarget, {
      status: updated.status,
      quoteCountdownStartedAt: updated.quoteCountdownStartedAt,
      quoteCountdownDueAt: updated.quoteCountdownDueAt
    });
  }
  await loadTableData();
}

async function handleQuoteComparisonExport(): Promise<void> {
  if (!detailData.value) {
    return;
  }
  const blob = await exportQuoteComparisonApi(detailData.value.id);
  downloadBlob(blob, `quote-comparison-${detailData.value.reqNo}.csv`);
}

function canSupplierConfirm(row: SupplierQuoteRequestVO): boolean {
  return Boolean(
    isSupplierUser.value &&
    row.myAwarded &&
    row.leaderConfirmStatus === "CONFIRMED" &&
    row.mySupplierConfirmStatus === "PENDING"
  );
}

function canSupplierWaive(row: SupplierQuoteRequestVO): boolean {
  return Boolean(
    isSupplierUser.value &&
    row.myAwarded &&
    row.leaderConfirmStatus === "CONFIRMED" &&
    row.mySupplierConfirmStatus === "PENDING"
  );
}

function canSubmitQuote(row: SupplierQuoteRequestVO): boolean {
  return isSupplierUser.value
    && row.status === "QUOTING"
    && !row.supplierBiddingSuspended
    && !row.supplierBanned
    && !quoteDeadlineExpired(row);
}

function canSupplierExport(row: SupplierQuoteRequestVO): boolean {
  return Boolean(
    isSupplierUser.value &&
    row.myAwarded &&
    row.leaderConfirmStatus === "CONFIRMED" &&
    row.mySupplierConfirmStatus === "CONFIRMED"
  );
}

async function handleSupplierConfirm(row: SupplierQuoteRequestVO): Promise<void> {
  await openAwardDialog(row);
}

async function handleSupplierWaive(row: SupplierQuoteRequestVO): Promise<void> {
  await openAwardDialog(row);
}

async function handleSupplierExport(row: SupplierQuoteRequestVO): Promise<void> {
  const blob = await exportAwardApi(row.id);
  downloadBlob(blob, `中标清单-${row.reqNo || "采购申请"}.xlsx`);
}

async function openAwardDialog(row: SupplierQuoteRequestVO): Promise<void> {
  awardTarget.value = row;
  awardQuote.value = null;
  awardWaiveRemark.value = "";
  awardDialogVisible.value = true;
  awardLoading.value = true;
  try {
    awardQuote.value = await getAwardConfirmationDetailApi(row.id);
  } finally {
    awardLoading.value = false;
  }
}

async function handleAwardDialogConfirm(): Promise<void> {
  if (!awardTarget.value) {
    return;
  }
  const confirmed = await ElMessageBox.confirm("确认接受当前中标明细、价格和规格？", "确认中标", {
    confirmButtonText: "确认中标",
    cancelButtonText: "取消",
    type: "success"
  }).then(() => true).catch(() => false);
  if (!confirmed) {
    return;
  }
  awardActionLoading.value = true;
  try {
    await confirmAwardApi(awardTarget.value.id, { confirmRemark: "供应商页面确认中标" });
    awardQuote.value = await getAwardConfirmationDetailApi(awardTarget.value.id);
    await refreshAwardRelatedData();
    try {
      await handleSupplierExport(awardTarget.value);
      ElMessage.success("供应商确认成功，已开始下载中标清单");
    } catch {
      ElMessage.warning("供应商确认成功，但中标清单自动下载失败，可点击“下载中标清单”重试");
    }
  } finally {
    awardActionLoading.value = false;
  }
}

async function handleAwardDialogWaive(): Promise<void> {
  if (!awardTarget.value) {
    return;
  }
  const remark = awardWaiveRemark.value.trim();
  if (!remark) {
    ElMessage.warning("请先填写放弃中标原因");
    return;
  }
  const confirmed = await ElMessageBox.confirm("放弃后系统会自动递补下一名供应商，确认放弃中标？", "放弃中标确认", {
    confirmButtonText: "放弃中标",
    cancelButtonText: "取消",
    type: "warning"
  }).then(() => true).catch(() => false);
  if (!confirmed) {
    return;
  }
  awardActionLoading.value = true;
  try {
    await waiveAwardApi(awardTarget.value.id, { waiveRemark: remark });
    ElMessage.success("已放弃中标，系统将递补下一名供应商");
    awardDialogVisible.value = false;
    await refreshAwardRelatedData();
  } finally {
    awardActionLoading.value = false;
  }
}

async function handleAwardDialogExport(): Promise<void> {
  if (!awardTarget.value) {
    return;
  }
  await handleSupplierExport(awardTarget.value);
}

async function refreshAwardRelatedData(): Promise<void> {
  await loadTableData();
  if (detailData.value) {
    myQuote.value = await getMyQuoteApi(detailData.value.id);
  }
}

async function handleSupplierConfirmFromDetail(): Promise<void> {
  if (!detailData.value) {
    return;
  }
  await handleSupplierConfirm({
    id: detailData.value.id,
    reqNo: detailData.value.reqNo,
    reqTitle: detailData.value.reqTitle,
    reqType: detailData.value.reqType,
    regionId: detailData.value.regionId,
    deptName: detailData.value.deptName,
    receivingCity: detailData.value.receivingCity,
    requestAmount: null,
    needDate: detailData.value.needDate,
    status: detailData.value.status,
    approvalStatus: detailData.value.approvalStatus,
    myAwarded: true,
    mySupplierConfirmStatus: myQuote.value?.supplierConfirmStatus || null,
    leaderConfirmStatus: detailData.value.leaderConfirmStatus,
    quoteCountdownStartedAt: detailData.value.quoteCountdownStartedAt,
    quoteCountdownDueAt: detailData.value.quoteCountdownDueAt
  });
  myQuote.value = await getMyQuoteApi(detailData.value.id);
}

async function handleSupplierWaiveFromDetail(): Promise<void> {
  if (!detailData.value) {
    return;
  }
  await handleSupplierWaive({
    id: detailData.value.id,
    reqNo: detailData.value.reqNo,
    reqTitle: detailData.value.reqTitle,
    reqType: detailData.value.reqType,
    regionId: detailData.value.regionId,
    deptName: detailData.value.deptName,
    receivingCity: detailData.value.receivingCity,
    requestAmount: null,
    needDate: detailData.value.needDate,
    status: detailData.value.status,
    approvalStatus: detailData.value.approvalStatus,
    myAwarded: true,
    mySupplierConfirmStatus: myQuote.value?.supplierConfirmStatus || null,
    leaderConfirmStatus: detailData.value.leaderConfirmStatus,
    quoteCountdownStartedAt: detailData.value.quoteCountdownStartedAt,
    quoteCountdownDueAt: detailData.value.quoteCountdownDueAt
  });
  myQuote.value = await getMyQuoteApi(detailData.value.id);
}

async function handleSupplierExportFromDetail(): Promise<void> {
  if (!detailData.value) {
    return;
  }
  await handleSupplierExport({
    id: detailData.value.id,
    reqNo: detailData.value.reqNo,
    reqTitle: detailData.value.reqTitle,
    reqType: detailData.value.reqType,
    regionId: detailData.value.regionId,
    deptName: detailData.value.deptName,
    receivingCity: detailData.value.receivingCity,
    requestAmount: null,
    needDate: detailData.value.needDate,
    status: detailData.value.status,
    approvalStatus: detailData.value.approvalStatus,
    myAwarded: true,
    leaderConfirmStatus: detailData.value.leaderConfirmStatus,
    quoteCountdownStartedAt: detailData.value.quoteCountdownStartedAt,
    quoteCountdownDueAt: detailData.value.quoteCountdownDueAt
  });
}

function openImportDialog(): void {
  quoteImportFileInput.value?.click();
}

function toggleWaived(line: QuoteLineForm): void {
  line.waived = !line.waived;
  if (line.waived) {
    clearReplacementErrors(line);
    quoteTableRef.value?.toggleRowExpansion(line, false);
    line.replacementFlag = false;
    line.replacementItemName = "";
    line.replacementSpecModel = "";
    line.replacementEcommerceUrl = "";
    line.replacementUnitPrice = null;
    line.replacementReason = "";
    line.replacementCondition = "NEW";
    line.replacementSecondHandRemark = "";
    line.unitPrice = null;
    line.leadTimeDays = null;
  } else {
    line.leadTimeDays = line.leadTimeDays || 7;
  }
}

async function handleReplacementFlagChange(line: QuoteLineForm): Promise<void> {
  clearReplacementErrors(line);
  if (!line.replacementFlag) {
    quoteTableRef.value?.toggleRowExpansion(line, false);
    return;
  }
  await revealReplacementLine(line, true);
}

function normalizeReplacementLink(line: QuoteLineForm): void {
  if (!line.replacementEcommerceUrl.trim()) {
    return;
  }
  const result = normalizeSupplierProductLinkInput(
    line.replacementEcommerceUrl,
    quoteTarget.internalProductCodeRule
  );
  if (result.error) {
    replacementValidationErrors[line.purchaseRequestItemId] = {
      ...(replacementValidationErrors[line.purchaseRequestItemId] || {}),
      url: result.error
    };
    return;
  }
  line.replacementEcommerceUrl = result.value;
  clearReplacementFieldError(line, "url");
}

async function revealReplacementLine(line: QuoteLineForm, focusMissingField: boolean): Promise<void> {
  quoteTableRef.value?.toggleRowExpansion(line, true);
  await nextTick();
  if (!focusMissingField) {
    return;
  }
  const fieldClass = !line.replacementItemName.trim()
    ? "replacement-name-input"
    : !line.replacementSpecModel.trim()
      ? "replacement-spec-input"
      : Number(line.replacementUnitPrice) <= 0
        ? "replacement-price-input"
        : !normalizeOptionalUrl(line.replacementEcommerceUrl)
          ? "replacement-url-input"
          : "replacement-reason-input";
  const input = document.querySelector<HTMLInputElement>(
    `.${fieldClass}[data-item-id="${line.purchaseRequestItemId}"] input`
  );
  input?.scrollIntoView({ block: "center", behavior: "smooth" });
  input?.focus();
}

function replacementFieldError(line: QuoteLineForm, field: ReplacementValidationField): string {
  return replacementValidationErrors[line.purchaseRequestItemId]?.[field] || "";
}

function clearReplacementFieldError(line: QuoteLineForm, field: ReplacementValidationField): void {
  const errors = replacementValidationErrors[line.purchaseRequestItemId];
  if (!errors?.[field]) {
    return;
  }
  delete errors[field];
  if (Object.keys(errors).length === 0) {
    delete replacementValidationErrors[line.purchaseRequestItemId];
  }
}

function clearReplacementErrors(line: QuoteLineForm): void {
  delete replacementValidationErrors[line.purchaseRequestItemId];
}

function clearAllReplacementErrors(): void {
  for (const itemId of Object.keys(replacementValidationErrors)) {
    delete replacementValidationErrors[Number(itemId)];
  }
}

async function exportQuoteTemplate(): Promise<void> {
  const blob = await exportQuoteTemplateApi(buildQuoteTemplateRows());
  downloadBlob(blob, `报价模板-${quoteTarget.reqNo || "采购申请"}.xlsx`);
}

function buildQuoteTemplateRows(): string[][] {
  return [
    [
      "行号（勿改）",
      "物料编码（勿改）",
      "商品名称（勿改）",
      "规格型号（勿改）",
      "电商链接（勿改）",
      "收货城市（勿改）",
      "数量（勿改）",
      "单位（勿改）",
      "是否放弃报价（可填）",
      "原商品成色（可填）",
      "原商品二手说明（可填）",
      "报价单价（可填）",
      "交期天数（可填，默认7天）",
      "报价备注（可填）",
      "是否替换商品（可填）",
      "替换商品名称（替换时必填）",
      "替换规格型号（替换时必填）",
      "替换商品链接（替换时必填）",
      "替换商品成色（可填）",
      "替换商品二手说明（可填）",
      "替换商品单价（可填）",
      "替换原因（替换时必填）",
      "报价说明（勿改）"
    ],
    ...quoteLines.value.map((line) => [
      String(line.lineNo),
      line.itemCode,
      line.itemName,
      line.specModel,
      line.ecommerceUrl || "",
      line.receivingCity || currentReceivingCity.value || "",
      String(line.qty),
      line.unit,
      line.waived ? "是" : "否",
      line.waived ? "" : conditionLabel(line.originalCondition),
      line.waived || line.originalCondition !== "USED" ? "" : line.originalSecondHandRemark,
      line.waived ? "" : String(line.unitPrice || ""),
      line.waived ? "" : String(line.leadTimeDays || ""),
      line.quoteRemark || "",
      line.replacementFlag ? "是" : "否",
      line.replacementFlag ? line.replacementItemName : "",
      line.replacementFlag ? line.replacementSpecModel : "",
      line.replacementFlag ? line.replacementEcommerceUrl : "",
      line.replacementFlag ? conditionLabel(line.replacementCondition) : "",
      line.replacementFlag && line.replacementCondition === "USED" ? line.replacementSecondHandRemark : "",
      line.replacementFlag ? String(line.replacementUnitPrice || "") : "",
      line.replacementFlag ? line.replacementReason : "",
      freightIncludedNotice
    ])
  ];
}

async function handleQuoteImportFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) {
    return;
  }
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    ElMessage.warning("请上传 Excel 文件");
    return;
  }
  quoteImportLoading.value = true;
  try {
    const rows = await importQuotePreviewApi(file);
    applyImportRows(rows);
  } finally {
    quoteImportLoading.value = false;
  }
}

function applyImportRows(rows: string[][]): void {
  const nonEmptyRows = rows
    .map((cells, index) => ({ cells, rowNumber: index + 1 }))
    .filter((row) => row.cells.some((cell) => cell.trim()));
  if (nonEmptyRows.length === 0) {
    ElMessage.warning("未导入有效报价，请检查格式");
    return;
  }
  const header = nonEmptyRows[0].cells;
  const hasHeader = header.some((part) => /行号|物料编码|商品名称|规格型号|报价单价|交期天数/.test(part));
  const indexMap = hasHeader ? buildImportHeaderIndex(header) : null;
  if (indexMap) {
    const missingHeaders = ["行号", "物料编码", "商品名称", "规格型号"].filter((name) => indexMap[normalizeImportHeaderName(name)] === undefined);
    if (missingHeaders.length > 0) {
      void ElMessageBox.alert(`导入模板缺少必填列：${missingHeaders.join("、")}。请重新导出报价模板后填写。`, "导入失败", {
        type: "error",
        confirmButtonText: "知道了"
      });
      return;
    }
  }
  const dataRows = hasHeader ? nonEmptyRows.slice(1) : nonEmptyRows;
  const errors: string[] = [];
  const usedTargets = new Set<number>();
  const validRows: ValidImportQuoteRow[] = [];
  for (const row of dataRows) {
    const parts = row.cells;
    if (parts.length < 4) {
      errors.push(`第 ${row.rowNumber} 行缺少物料编码、商品名称或规格型号列`);
      continue;
    }
    const target = resolveImportTarget(parts, indexMap, row.rowNumber, usedTargets, errors);
    if (!target) {
      continue;
    }
    const waived = parseYesNo(readImportValue(parts, indexMap, "是否放弃报价", 8));
    const originalCondition = waived
      ? "NEW"
      : parseOptionalProductCondition(readImportValue(parts, indexMap, "原商品成色", 9));
    const originalSecondHandRemark = waived ? "" : readImportValue(parts, indexMap, "原商品二手说明", 10);
    const replacementFlag = waived ? false : parseYesNo(readImportValue(parts, indexMap, "是否替换商品", 14));
    const replacementItemName = replacementFlag ? readImportValue(parts, indexMap, "替换商品名称", 15) : "";
    const replacementSpecModel = replacementFlag ? readImportValue(parts, indexMap, "替换规格型号", 16) : "";
    const replacementEcommerceUrlInput = replacementFlag ? readImportValue(parts, indexMap, "替换商品链接", 17) : "";
    const replacementUrlResult = normalizeSupplierProductLinkInput(
      replacementEcommerceUrlInput,
      quoteTarget.internalProductCodeRule
    );
    const replacementEcommerceUrl = replacementFlag && !replacementUrlResult.error ? replacementUrlResult.value : "";
    const replacementCondition = replacementFlag
      ? parseOptionalProductCondition(readImportValue(parts, indexMap, "替换商品成色", 18))
      : "NEW";
    const replacementSecondHandRemark = replacementFlag
      ? readImportValue(parts, indexMap, "替换商品二手说明", 19)
      : "";
    const replacementUnitPrice = replacementFlag ? parseNumber(readImportValue(parts, indexMap, "替换商品单价", 20)) : null;
    const replacementReason = replacementFlag ? readImportValue(parts, indexMap, "替换原因", 21) : "";
    const quoteRemark = readImportValue(parts, indexMap, "报价备注", 13);
    if (waived) {
      validRows.push({
        target,
        replacementFlag,
        replacementItemName: "",
        replacementSpecModel: "",
        replacementEcommerceUrl: "",
        replacementUnitPrice: null,
        replacementReason: "",
        originalCondition: "NEW",
        originalSecondHandRemark,
        replacementCondition: "NEW",
        replacementSecondHandRemark: "",
        waived,
        unitPrice: null,
        leadTimeDays: null,
        quoteRemark
      });
      continue;
    }
    const unitPrice = parseNumber(readImportValue(parts, indexMap, "报价单价", 11));
    const leadTimeDays = parseInt(readImportValue(parts, indexMap, "交期天数", 12), 10);
    if (unitPrice <= 0 || !Number.isFinite(leadTimeDays) || leadTimeDays <= 0) {
      errors.push(`第 ${row.rowNumber} 行报价单价或交期天数无效`);
      continue;
    }
    if (!originalCondition) {
      errors.push(`第 ${row.rowNumber} 行原商品成色必须填写全新或二手`);
      continue;
    }
    if (originalCondition === "USED" && !originalSecondHandRemark) {
      errors.push(`第 ${row.rowNumber} 行原商品为二手，请填写原商品二手说明`);
      continue;
    }
    if (replacementFlag && (!replacementItemName || !replacementSpecModel || !replacementUnitPrice || replacementUnitPrice <= 0 || !replacementReason)) {
      errors.push(`第 ${row.rowNumber} 行已标记替换商品，请填写名称、规格型号、单价和替换原因`);
      continue;
    }
    if (replacementFlag && (!replacementEcommerceUrlInput || replacementUrlResult.error)) {
      errors.push(`第 ${row.rowNumber} 行替换商品链接无效：${replacementUrlResult.error || "请填写商品详情页链接"}`);
      continue;
    }
    if (replacementFlag && !replacementCondition) {
      errors.push(`第 ${row.rowNumber} 行替换商品成色必须填写全新或二手`);
      continue;
    }
    if (replacementFlag && replacementCondition === "USED" && !replacementSecondHandRemark) {
      errors.push(`第 ${row.rowNumber} 行替换商品为二手，请填写替换商品二手说明`);
      continue;
    }
    validRows.push({
      target,
      replacementFlag,
      replacementItemName,
      replacementSpecModel,
      replacementEcommerceUrl,
      replacementUnitPrice,
      replacementReason,
      originalCondition,
      originalSecondHandRemark,
      replacementCondition: replacementCondition ?? "NEW",
      replacementSecondHandRemark,
      waived,
      unitPrice,
      leadTimeDays,
      quoteRemark
    });
  }

  if (errors.length > 0) {
    const message = errors.slice(0, 8).join("\n") + (errors.length > 8 ? `\n还有 ${errors.length - 8} 条错误未展示` : "");
    void ElMessageBox.alert(message, "导入失败", {
      type: "error",
      confirmButtonText: "知道了"
    });
    return;
  }
  if (validRows.length === 0) {
    ElMessage.warning("未导入有效报价，请检查格式");
    return;
  }
  for (const row of validRows) {
    row.target.replacementFlag = row.replacementFlag;
    row.target.replacementItemName = row.replacementItemName;
    row.target.replacementSpecModel = row.replacementSpecModel;
    row.target.replacementEcommerceUrl = row.replacementEcommerceUrl;
    row.target.replacementUnitPrice = row.replacementUnitPrice;
    row.target.replacementReason = row.replacementReason;
    row.target.originalCondition = row.originalCondition;
    row.target.originalSecondHandRemark = row.originalSecondHandRemark;
    row.target.replacementCondition = row.replacementCondition;
    row.target.replacementSecondHandRemark = row.replacementSecondHandRemark;
    row.target.waived = row.waived;
    row.target.unitPrice = row.unitPrice;
    row.target.leadTimeDays = row.leadTimeDays;
    row.target.quoteRemark = row.quoteRemark;
  }
  ElMessage.success(`已导入 ${validRows.length} 行报价`);
}

function buildImportHeaderIndex(header: string[]): Record<string, number> {
  return header.reduce<Record<string, number>>((map, name, index) => {
    map[normalizeImportHeaderName(name)] = index;
    return map;
  }, {});
}

function readImportValue(parts: string[], indexMap: Record<string, number> | null, header: string, fallbackIndex: number): string {
  const index = indexMap?.[normalizeImportHeaderName(header)] ?? fallbackIndex;
  return parts[index]?.trim() || "";
}

function normalizeImportHeaderName(value: string): string {
  return value
    .replace(/[（(]\s*(?:可填|替换时必填|勿改|不可修改|请勿修改|系统生成|系统字段)[^）)]*[）)]/g, "")
    .replace(/\s/g, "")
    .trim();
}

function resolveImportTarget(
  parts: string[],
  indexMap: Record<string, number> | null,
  rowNumber: number,
  usedTargets: Set<number>,
  errors: string[]
): QuoteLineForm | null {
  const lineNo = readImportValue(parts, indexMap, "行号", 0);
  const itemCode = readImportValue(parts, indexMap, "物料编码", 1);
  const itemName = readImportValue(parts, indexMap, "商品名称", 2);
  const specModel = readImportValue(parts, indexMap, "规格型号", 3);
  if (!lineNo && (!itemCode || !itemName || !specModel)) {
    errors.push(`第 ${rowNumber} 行必须保留行号，或同时填写物料编码、商品名称、规格型号`);
    return null;
  }

  let target: QuoteLineForm | undefined;
  if (lineNo) {
    target = quoteLines.value.find((line) => String(line.lineNo) === lineNo);
    if (!target) {
      errors.push(`第 ${rowNumber} 行的行号 ${lineNo} 不在当前报价明细中`);
      return null;
    }
  } else {
    const matches = quoteLines.value.filter(
      (line) =>
        sameImportValue(line.itemCode, itemCode) &&
        sameImportValue(line.itemName, itemName) &&
        sameImportValue(line.specModel, specModel)
    );
    if (matches.length !== 1) {
      errors.push(matches.length === 0
        ? `第 ${rowNumber} 行不在当前报价明细中`
        : `第 ${rowNumber} 行商品重复，请保留模板中的行号后再导入`);
      return null;
    }
    target = matches[0];
  }

  if (!sameImportValue(target.itemCode, itemCode) || !sameImportValue(target.itemName, itemName) || !sameImportValue(target.specModel, specModel)) {
    errors.push(`第 ${rowNumber} 行物料编码、商品名称或规格型号与当前报价明细不一致`);
    return null;
  }
  if (usedTargets.has(target.purchaseRequestItemId)) {
    errors.push(`第 ${rowNumber} 行重复导入了第 ${target.lineNo} 行商品`);
    return null;
  }
  usedTargets.add(target.purchaseRequestItemId);
  return target;
}

function sameImportValue(left: string, right: string): boolean {
  return normalizeImportValue(left) === normalizeImportValue(right);
}

function normalizeImportValue(value: string): string {
  return String(value || "").replace(/\s/g, "").trim();
}

function parseNumber(value: string): number {
  return Number(String(value).replace(/[¥,\s]/g, ""));
}

function parseYesNo(value: string): boolean {
  return ["是", "Y", "YES", "TRUE", "1"].includes(value.trim().toUpperCase());
}

function parseProductCondition(value: string): "NEW" | "USED" | null {
  const normalized = value.trim().toUpperCase();
  if (normalized === "二手" || normalized === "USED" || normalized === "翻新") {
    return "USED";
  }
  if (normalized === "全新" || normalized === "NEW") {
    return "NEW";
  }
  return null;
}

function parseOptionalProductCondition(value: string): "NEW" | "USED" | null {
  return value.trim() ? parseProductCondition(value) : "NEW";
}

function conditionLabel(condition?: string | null): string {
  return condition === "USED" ? "二手" : condition === "NEW" ? "全新" : "待确认";
}

function quoteLineRuleLabel(line: QuoteLineForm): string {
  const catalogRule = "目录内按固定资产，目录外按低值易耗";
  return line.originalCondition === "USED" && Number(line.unitPrice || 0) >= 1000
    ? `${catalogRule}；该二手报价需特殊审批`
    : catalogRule;
}

function quoteLineRuleType(line: QuoteLineForm): "success" | "warning" | "info" {
  if (line.originalCondition !== "USED") {
    return "info";
  }
  return Number(line.unitPrice || 0) >= 1000 ? "warning" : "info";
}

function replacementQuoteLineRuleLabel(line: QuoteLineForm): string {
  const catalogRule = "目录内按固定资产，目录外按低值易耗";
  return line.replacementCondition === "USED" && Number(line.replacementUnitPrice || 0) >= 1000
    ? `${catalogRule}；该二手替换报价需特殊审批`
    : catalogRule;
}

function replacementQuoteLineRuleType(line: QuoteLineForm): "warning" | "info" {
  return line.replacementCondition === "USED" && Number(line.replacementUnitPrice || 0) >= 1000
    ? "warning"
    : "info";
}

function assetClassificationLabel(classification?: string | null): string {
  const labels: Record<string, string> = {
    ASSET: "固定资产",
    LOW_VALUE: "低值易耗",
    SPECIAL_APPROVAL: "固定资产",
    NOT_ASSET: "低值易耗",
    MANUAL_REVIEW: "待人工确认",
    PENDING: "M3复核中"
  };
  return labels[classification || ""] || "待确认";
}

function assetClassificationType(classification?: string | null): "success" | "warning" | "danger" | "info" {
  if (classification === "ASSET") {
    return "success";
  }
  if (classification === "LOW_VALUE" || classification === "NOT_ASSET") {
    return "info";
  }
  if (classification === "SPECIAL_APPROVAL") {
    return "warning";
  }
  if (classification === "MANUAL_REVIEW") {
    return "danger";
  }
  return "info";
}

function assetCatalogText(row: SupplierQuoteItemVO, replacement = false): string {
  const categoryCode = replacement ? row.replacementAssetCategoryCode : row.originalAssetCategoryCode;
  const assetNameCode = replacement ? row.replacementAssetNameCode : row.originalAssetNameCode;
  const catalogName = replacement ? row.replacementAssetCatalogName : row.originalAssetCatalogName;
  if (!categoryCode || !assetNameCode) {
    const classification = replacement ? row.replacementAssetClassification : row.originalAssetClassification;
    return classification === "LOW_VALUE" || classification === "NOT_ASSET" ? "未命中资产目录" : "目录待确认";
  }
  return `${categoryCode}/${assetNameCode} ${catalogName || ""}`.trim();
}

function awardSpecialApprovalRequired(row: SupplierQuoteItemVO): boolean {
  return Boolean(
    row.replacementFlag && row.replacementReviewStatus === "APPROVED"
      ? row.replacementSpecialApprovalRequired
      : row.originalSpecialApprovalRequired
  );
}

function lineAmount(line: QuoteLineForm): number {
  if (line.waived) {
    return 0;
  }
  return Number(line.qty || 0) * Number(line.unitPrice || 0);
}

function replacementPriceDiffText(line: QuoteLineForm): string {
  const originalPrice = Number(line.unitPrice || 0);
  const replacementPrice = Number(line.replacementUnitPrice || 0);
  if (originalPrice <= 0 || replacementPrice <= 0) {
    return "价格差额待补充";
  }
  const difference = replacementPrice - originalPrice;
  const rate = Math.abs(difference / originalPrice) * 100;
  const direction = difference > 0 ? "高" : difference < 0 ? "低" : "持平";
  return difference === 0
    ? "替换价与原报价持平"
    : `替换价${direction} ${formatMoney(Math.abs(difference))}（${rate.toFixed(1)}%）`;
}

function replacementPriceDiffType(line: QuoteLineForm): "success" | "warning" | "danger" | "info" {
  const originalPrice = Number(line.unitPrice || 0);
  const replacementPrice = Number(line.replacementUnitPrice || 0);
  if (originalPrice <= 0 || replacementPrice <= 0 || replacementPrice === originalPrice) {
    return "info";
  }
  if (replacementPrice < originalPrice) {
    return "success";
  }
  return (replacementPrice - originalPrice) / originalPrice > 0.13 ? "danger" : "warning";
}

function formatMoney(value?: number | null): string {
  return Number(value ?? 0).toLocaleString("zh-CN", {
    style: "currency",
    currency: "CNY",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
}

function hasText(value?: string | null): boolean {
  return Boolean(String(value ?? "").trim());
}

function detailText(value?: string | null): string {
  return String(value ?? "").trim() || "-";
}

function normalizeUrl(url: string): string {
  return ecommerceUrlHref(url);
}

function normalizeOptionalUrl(url?: string | null): string {
  return normalizeUrl(String(url ?? "").trim());
}

function quoteStatusLabel(row: SupplierQuoteRequestVO): string {
  if (!isSupplierUser.value) {
    return "可查看";
  }
  if (row.myAwarded && row.leaderConfirmStatus === "CONFIRMED") {
    if (row.mySupplierConfirmStatus === "CONFIRMED") {
      return "中标";
    }
    if (row.mySupplierConfirmStatus === "WAIVED") {
      return "已放弃";
    }
    return "中标待确认";
  }
  if (row.status === "PENDING_APPROVAL") {
    return "报价审核中";
  }
  if (row.status === "APPROVED") {
    return "已审核";
  }
  if (row.supplierBanned) {
    return "已封禁";
  }
  if (row.supplierBiddingSuspended && !row.myQuoteStatus) {
    return "暂停投标";
  }
  if (!row.myQuoteStatus && row.status === "QUOTING" && quoteDeadlineExpired(row)) {
    return "超时放弃";
  }
  return row.myQuoteStatus ? "已报价" : "待报价";
}

function quoteStatusType(row: SupplierQuoteRequestVO): "success" | "warning" | "info" | "danger" {
  if (!isSupplierUser.value) {
    return "info";
  }
  if (row.myAwarded && row.leaderConfirmStatus === "CONFIRMED") {
    if (row.mySupplierConfirmStatus === "CONFIRMED") {
      return "success";
    }
    return row.mySupplierConfirmStatus === "WAIVED" ? "info" : "warning";
  }
  if (row.status === "PENDING_APPROVAL" || row.status === "APPROVED") {
    return "info";
  }
  if (row.supplierBanned) {
    return "danger";
  }
  if (row.supplierBiddingSuspended && !row.myQuoteStatus) {
    return "info";
  }
  if (!row.myQuoteStatus && row.status === "QUOTING" && quoteDeadlineExpired(row)) {
    return "danger";
  }
  return row.myQuoteStatus ? "success" : "warning";
}

function reviewStatusLabel(status?: string | null): string {
  if (status === "CONFIRMED") {
    return "报价审核通过";
  }
  return "待报价审核";
}

function reviewStatusType(status?: string | null): "success" | "warning" | "info" {
  if (status === "CONFIRMED") {
    return "success";
  }
  return "warning";
}

function supplierConfirmStatusLabel(status?: string | null): string {
  if (status === "CONFIRMED") {
    return "已确认";
  }
  if (status === "WAIVED") {
    return "已放弃";
  }
  if (status === "PENDING") {
    return "待确认";
  }
  return status || "-";
}

function displayAwardUnitPrice(row: SupplierQuoteItemVO): number | null {
  return row.replacementFlag && row.replacementReviewStatus === "APPROVED"
    ? row.replacementUnitPrice ?? row.unitPrice ?? null
    : row.unitPrice ?? null;
}

function displayAwardLineAmount(row: SupplierQuoteItemVO): number | null {
  return row.replacementFlag && row.replacementReviewStatus === "APPROVED"
    ? row.replacementLineAmount ?? row.lineAmount ?? null
    : row.lineAmount ?? null;
}

function quoteHasDeliveryAddress(quote?: SupplierQuoteVO | null): boolean {
  return Boolean(quote?.items?.some((item) => (canViewAllSupplierQuotes.value || item.awarded) && item.deliveryAddress));
}

function displayDeliveryAddress(item: SupplierQuoteItemVO): string {
  return canViewAllSupplierQuotes.value || item.awarded ? item.deliveryAddress || "-" : "-";
}

function isAwardWaived(quote?: SupplierQuoteVO | null): boolean {
  return quote?.supplierConfirmStatus === "WAIVED";
}

function awardWaiveReason(quote?: SupplierQuoteVO | null): string {
  const reason = quote?.supplierConfirmRemark?.trim();
  return `放弃中标原因：${reason || "未填写"}`;
}

function quoteAutoCompareText(row?: QuoteDeadlineSource & {
  directAwardApplied?: boolean;
  directAwardSupplierName?: string | null;
} | null): string {
  if (!row) {
    return "-";
  }
  if (row.status === "PENDING_APPROVAL") {
    return "已进入报价审核";
  }
  if (row.status === "APPROVED") {
    return "报价审核通过";
  }
  if (row.directAwardApplied) {
    return row.directAwardSupplierName
      ? `免三方比价，指派 ${row.directAwardSupplierName}`
      : "低额紧急定向，免三方比价";
  }
  if (!row.quoteCountdownDueAt) {
    return "开放 7 个自然日";
  }
  const dueTime = new Date(row.quoteCountdownDueAt);
  if (Number.isNaN(dueTime.getTime())) {
    return "报价截止时间待确认";
  }
  const remainingMs = dueTime.getTime() - countdownNow.value;
  const accelerated = Boolean(row.quoteCountdownStartedAt);
  if (remainingMs <= 0) {
    return accelerated ? "3小时倒计时已结束，等待系统自动比价" : "报价截止已到，未报价供应商视为放弃";
  }
  if (accelerated) {
    return `3小时倒计时剩余 ${formatDuration(remainingMs)}（截止 ${formatDateTime(row.quoteCountdownDueAt)}）`;
  }
  return `报价截止 ${formatDateTime(row.quoteCountdownDueAt)}，超时视为放弃`;
}

function quoteDeadlineDate(row?: QuoteDeadlineSource | null): Date | null {
  if (!row?.quoteCountdownDueAt) {
    return null;
  }
  const date = new Date(row.quoteCountdownDueAt);
  return Number.isNaN(date.getTime()) ? null : date;
}

function quoteDeadlineRemainingMs(row?: QuoteDeadlineSource | null): number | null {
  const due = quoteDeadlineDate(row);
  return due ? due.getTime() - countdownNow.value : null;
}

function quoteDeadlineExpired(row?: QuoteDeadlineSource | null): boolean {
  const remainingMs = quoteDeadlineRemainingMs(row);
  return row?.status === "QUOTING" && remainingMs != null && remainingMs <= 0;
}

function quoteDeadlineLabel(row?: QuoteDeadlineSource | null): string {
  if (row?.status === "PENDING_APPROVAL") {
    return "已进审核";
  }
  if (row?.status === "APPROVED") {
    return "已完成";
  }
  const remainingMs = quoteDeadlineRemainingMs(row);
  if (remainingMs == null) {
    return "待确认";
  }
  if (remainingMs <= 0) {
    return "已截止";
  }
  return `剩余 ${formatDeadlineDistance(remainingMs)}`;
}

function quoteDeadlineSubText(row?: QuoteDeadlineSource | null): string {
  if (!row?.quoteCountdownDueAt) {
    return "开放 7 个自然日";
  }
  if (quoteDeadlineExpired(row)) {
    return `截止 ${formatDateTime(row.quoteCountdownDueAt)}，超时放弃`;
  }
  return `最晚 ${formatDateTime(row.quoteCountdownDueAt)} 前提交`;
}

function quoteDeadlineTagType(row?: QuoteDeadlineSource | null): "success" | "warning" | "info" | "danger" {
  const remainingMs = quoteDeadlineRemainingMs(row);
  if (remainingMs == null || row?.status !== "QUOTING") {
    return "info";
  }
  if (remainingMs <= 0) {
    return "danger";
  }
  return remainingMs <= 24 * 60 * 60 * 1000 ? "warning" : "success";
}

function quoteDeadlineDetailText(row?: QuoteDeadlineSource | null): string {
  if (!row?.quoteCountdownDueAt) {
    return "开放 7 个自然日，截止时间待确认";
  }
  if (quoteDeadlineExpired(row)) {
    return `已于 ${formatDateTime(row.quoteCountdownDueAt)} 截止，未报价供应商按放弃报价处理`;
  }
  return `最晚 ${formatDateTime(row.quoteCountdownDueAt)} 前提交，超时按放弃报价处理`;
}

function quoteDeadlineAlertTitle(row?: QuoteDeadlineSource | null): string {
  if (quoteDeadlineExpired(row)) {
    return "报价截止时间已到";
  }
  return `最晚 ${formatDateTime(row?.quoteCountdownDueAt)} 前提交报价`;
}

function quoteDeadlineAlertDescription(row?: QuoteDeadlineSource | null): string {
  if (quoteDeadlineExpired(row)) {
    return "未提交报价的供应商将按放弃报价处理，管理员可根据实际情况延长报价时间。";
  }
  return "请在截止时间前提交或更新报价；超过截止时间未报价的供应商，将按放弃报价处理。";
}

function directAwardText(row?: { directAwardSupplierName?: string | null; directAwardReason?: string | null } | null): string {
  if (!row) {
    return "-";
  }
  if (row.directAwardReason) {
    return row.directAwardReason;
  }
  return row.directAwardSupplierName ? `已指派 ${row.directAwardSupplierName}` : "已启用低额紧急定向报价";
}

function formatDuration(milliseconds: number): string {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (num: number) => String(num).padStart(2, "0");
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}

function formatDeadlineDistance(milliseconds: number): string {
  const totalMinutes = Math.max(0, Math.ceil(milliseconds / 60000));
  const days = Math.floor(totalMinutes / 1440);
  const hours = Math.floor((totalMinutes % 1440) / 60);
  const minutes = totalMinutes % 60;
  if (days > 0) {
    return `${days}天${hours}小时`;
  }
  if (hours > 0) {
    return `${hours}小时${minutes}分钟`;
  }
  return `${minutes}分钟`;
}

function formatDateTime(value?: string | null): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const pad = (num: number) => String(num).padStart(2, "0");
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function awardStrategyLabel(strategy?: string | null): string {
  return strategy === "ITEM_LOWEST" ? "单项最低中标" : "总价最低中标";
}

function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

onMounted(() => {
  countdownTimer = window.setInterval(() => {
    countdownNow.value = Date.now();
  }, 1000);
  const reqNo = route.query.reqNo;
  if (typeof reqNo === "string") {
    queryModel.reqNo = reqNo;
  }
  loadTableData();
});

onUnmounted(() => {
  if (countdownTimer !== null) {
    window.clearInterval(countdownTimer);
    countdownTimer = null;
  }
});
</script>

<style scoped>
.pager-box {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-table,
.quote-table,
.quote-form,
.quote-section {
  margin-top: 16px;
}

.quote-section-head,
.supplier-quote-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 14px 0 10px;
  color: var(--app-text);
}

.quote-section-head span,
.supplier-quote-title span {
  color: var(--app-text-muted);
  font-size: 13px;
}

.quote-section-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.quote-section-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.supplier-quote-block {
  padding-top: 10px;
  border-top: 1px solid var(--app-line-soft);
}

.quote-waive-reason {
  margin-top: 10px;
}

.award-remark-input {
  margin-top: 16px;
}

.quote-limited-actions {
  margin-top: 12px;
}

.quote-alert {
  margin-bottom: 16px;
}

.quote-list-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  line-height: 1;
}

.quote-list-actions :deep(.el-button) {
  height: auto;
  min-height: 20px;
  margin-left: 0;
  padding: 0;
  white-space: nowrap;
}

.quote-deadline-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.quote-deadline-cell span {
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.35;
}

.direct-award-alert {
  margin-top: 14px;
}

.quote-alert-content {
  display: grid;
  gap: 4px;
}

.quote-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.quote-toolbar-actions,
.quote-section-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.quote-section-actions--compact {
  flex: 0 0 auto;
  gap: 6px;
}

.quote-section-actions--compact :deep(.el-button) {
  margin-left: 0;
  padding: 5px 10px;
}

.quote-total-note {
  margin-left: 14px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.quote-input {
  width: 100%;
}

.condition-editor,
.asset-review-cell {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.condition-select {
  width: 100%;
}

.replacement-extra-row {
  margin-top: 10px;
}

.asset-review-cell > span {
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.asset-review-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
}

.replacement-edit-panel {
  padding: 10px 12px;
  background: var(--app-bg-soft);
  border: 1px solid var(--app-line-soft);
  border-radius: 6px;
}

.replacement-rule-tag {
  margin-top: 12px;
}

.replacement-compare-preview {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 28px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  margin-top: 12px;
  padding: 12px;
  background: #fff;
  border: 1px solid var(--app-line-soft);
  border-radius: 6px;
}

.replacement-compare-card {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.replacement-compare-card > span,
.replacement-compare-card small {
  color: var(--app-text-muted);
  font-size: 12px;
}

.replacement-compare-card strong,
.replacement-compare-card small {
  overflow-wrap: anywhere;
}

.replacement-compare-card--target strong {
  color: var(--el-color-primary);
}

.replacement-compare-arrow {
  color: var(--el-color-primary);
  font-size: 22px;
  font-weight: 600;
  text-align: center;
}

.replacement-edit-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: var(--app-text);
}

.replacement-edit-head span {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 400;
}

.replacement-edit-panel .field-label {
  margin-bottom: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.required-label,
.replacement-field-error {
  color: var(--el-color-danger);
}

.required-label {
  margin-left: 4px;
}

.replacement-field-error {
  margin-top: 4px;
  font-size: 12px;
}

.is-replacement-invalid :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

@media (max-width: 1100px) {
  .replacement-compare-preview {
    grid-template-columns: 1fr;
  }

  .replacement-compare-arrow {
    transform: rotate(90deg);
  }
}

.replacement-detail-cell {
  display: grid;
  gap: 4px;
  min-width: 0;
  line-height: 1.45;
}

.replacement-detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--app-text);
  font-weight: 600;
}

.replacement-detail-title span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.replacement-detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  color: var(--app-text-muted);
  font-size: 12px;
}

.replacement-detail-reason,
.replacement-detail-remark {
  color: var(--app-text-muted);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.muted-text {
  color: var(--app-text-muted);
  font-size: 13px;
}

.hidden-file-input {
  display: none;
}
</style>
