<template>
  <VnetPage :title="pageTitle">
    <template #actions>
      <el-button v-if="detailPageVisible" @click="handleDetailBack">{{ detailBackLabel }}</el-button>
      <el-button @click="loadAll">刷新</el-button>
    </template>

    <template v-if="!detailPageVisible">
      <VnetPanel :title="filterPanelTitle">
        <el-tag v-if="canManageAllSupplierSales" class="all-supplier-access-tag" type="warning" effect="plain">
          全供应商管理
        </el-tag>
        <div class="order-tabs">
          <button
            v-for="tab in orderStatusTabs"
            :key="tab.value"
            class="order-tab"
            :class="{ active: queryModel.status === tab.value }"
            @click="switchStatus(tab.value)"
          >
            {{ tab.label }}
          </button>
        </div>
        <el-form :inline="true" :model="queryModel" class="vnet-search-form order-search">
          <el-form-item label="关键词">
            <el-input
              v-model="queryModel.keyword"
              clearable
              :placeholder="keywordPlaceholder"
              @keyup.enter="handleSearch"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">查询</el-button>
            <el-button @click="resetQuery">重置</el-button>
          </el-form-item>
        </el-form>
      </VnetPanel>

      <VnetPanel :title="listPanelTitle">
        <div v-loading="loading" class="order-list">
          <div class="kind-tabs">
            <button
              v-for="tab in groupKindTabs"
              :key="tab.value"
              class="kind-tab"
              :class="{ active: activeGroupKind === tab.value }"
              @click="switchGroupKind(tab.value)"
            >
              <span>{{ tab.label }}</span>
              <em>{{ tab.count }}</em>
            </button>
          </div>
          <el-empty v-if="!loading && pagedGroups.length === 0" :description="emptyDescription" />
          <el-table v-else :data="pagedGroups" row-key="key" class="group-table">
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-tag :type="groupKindTagType(row.kind)" effect="plain">{{ groupKindLabel(row.kind) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="单号" min-width="210">
              <template #default="{ row }">
                <el-button link type="primary" class="order-no-link" @click="openGroupDetail(row)">
                  {{ row.groupNo }}
                </el-button>
                <div class="muted-line">{{ formatDateTime(row.latestOrderTime) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="中标单位" min-width="180">
              <template #default="{ row }">{{ supplierSummary(row) }}</template>
            </el-table-column>
            <el-table-column label="商品" width="100" align="center">
              <template #default="{ row }">{{ row.items.length }} 项</template>
            </el-table-column>
            <el-table-column label="合计金额" width="130" align="right">
              <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column label="到货盘点" min-width="180">
              <template #default="{ row }">
                <div class="status-stack">
                  <span>待收货 {{ row.waitingReceiptCount }}</span>
                  <span>已收货 {{ row.receivedCount }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="售后" width="140">
              <template #default="{ row }">
                <span v-if="row.afterSaleCount">{{ row.afterSaleCount }} 单，待处理 {{ row.pendingAfterSaleCount }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="130">
              <template #default="{ row }">
                <el-tag :type="groupStatusType(row)" effect="light">{{ groupStatusLabel(row) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button link type="primary" @click="openGroupDetail(row)">查看明细</el-button>
                  <el-button v-if="canExportOrderGroup(row)" link type="primary" @click="exportOrderGroup(row)">导出PDF</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

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
    </template>

    <template v-else>
      <VnetPanel :title="detailPanelTitle">
        <div v-loading="detailLoading" class="detail-page">
          <div class="detail-toolbar">
            <el-button @click="handleDetailBack">{{ detailBackLabel }}</el-button>
            <div class="detail-title">
              <strong>{{ detailHeading }}</strong>
              <span>{{ detailSubtitle }}</span>
            </div>
            <el-button
              v-if="currentGroup?.kind !== 'CENTRALIZED' && currentGroup && canExportOrderGroup(currentGroup)"
              type="primary"
              @click="exportOrderGroup(currentGroup)"
            >
              导出操作订单PDF
            </el-button>
            <el-button
              v-else-if="selectedSupplierArchive && canExportSupplierArchive(selectedSupplierArchive)"
              type="primary"
              @click="exportSupplierArchive(selectedSupplierArchive)"
            >
              导出供应商归档PDF
            </el-button>
          </div>

          <template v-if="currentGroup?.kind === 'CENTRALIZED'">
            <dl v-if="currentGroup" class="detail-summary archive-summary">
              <div>
                <dt>集采单号</dt>
                <dd>{{ currentGroup.groupNo }}</dd>
              </div>
              <div>
                <dt>机房</dt>
                <dd>{{ roomArchives.length }} 个</dd>
              </div>
              <div>
                <dt>中标供应商</dt>
                <dd>{{ currentGroup.supplierNames.length }} 家</dd>
              </div>
              <div>
                <dt>中标商品</dt>
                <dd>{{ currentGroup.items.length }} 项</dd>
              </div>
              <div>
                <dt>中标金额</dt>
                <dd>{{ formatMoney(currentGroup.totalAmount) }}</dd>
              </div>
              <div>
                <dt>待收货</dt>
                <dd>{{ currentGroup.waitingReceiptCount }} 项</dd>
              </div>
            </dl>

            <section v-if="archiveLevel === 'ROOMS'" class="archive-section">
              <div class="archive-section-heading">
                <strong>机房归档</strong>
                <span>按需求所属机房查看中标供应商和订单资料</span>
              </div>
              <el-table :data="roomArchives" row-key="key" class="detail-table archive-table">
                <el-table-column label="机房 / 部门" min-width="300">
                  <template #default="{ row }">
                    <div class="archive-primary-cell">
                      <strong>{{ row.name }}</strong>
                      <span>{{ row.sourceRequestCount }} 张申请 · {{ row.items.length }} 项商品</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="中标供应商" width="140" align="center">
                  <template #default="{ row }">{{ row.supplierCount }} 家</template>
                </el-table-column>
                <el-table-column label="中标金额" width="150" align="right">
                  <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
                </el-table-column>
                <el-table-column label="交付进度" min-width="220">
                  <template #default="{ row }">
                    <div class="status-stack">
                      <span>待收货 {{ row.waitingReceiptCount }} 项</span>
                      <span>已收货 {{ row.receivedCount }} 项</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openRoomArchive(row)">查看供应商</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <section v-else-if="archiveLevel === 'SUPPLIERS'" class="archive-section">
              <div class="archive-section-heading">
                <strong>{{ selectedRoomArchive?.name || "-" }} · 中标供应商</strong>
                <span>{{ selectedRoomArchive?.items.length || 0 }} 项商品分布</span>
              </div>
              <el-table :data="supplierArchives" row-key="key" class="detail-table archive-table">
                <el-table-column label="中标供应商" min-width="250">
                  <template #default="{ row }">
                    <div class="archive-primary-cell">
                      <strong>{{ row.name }}</strong>
                      <span>{{ supplierAwardSummary(row) }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="中标商品" width="120" align="center">
                  <template #default="{ row }">{{ row.items.length }} 项</template>
                </el-table-column>
                <el-table-column label="中标金额" width="150" align="right">
                  <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
                </el-table-column>
                <el-table-column label="订单交付" min-width="190">
                  <template #default="{ row }">
                    <div class="status-stack">
                      <span>待收货 {{ row.waitingReceiptCount }} 项</span>
                      <span>已收货 {{ row.receivedCount }} 项</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="发票归档" min-width="190">
                  <template #default="{ row }">
                    <div class="status-stack">
                      <span>已上传 {{ row.invoiceUploadedCount }} / {{ row.items.length }}</span>
                      <span>已确认 {{ row.invoiceAcceptedCount }} 份</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openSupplierArchive(row)">进入归档</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <section v-else-if="selectedSupplierArchive" class="archive-section supplier-archive">
              <div class="supplier-banner">
                <div class="archive-primary-cell">
                  <strong>{{ selectedSupplierArchive.name }}</strong>
                  <span>{{ selectedRoomArchive?.name }} · {{ selectedSupplierArchive.items.length }} 项中标商品</span>
                </div>
                <div class="supplier-banner-metric">
                  <span>中标金额</span>
                  <strong>{{ formatMoney(selectedSupplierArchive.totalAmount) }}</strong>
                </div>
                <div class="supplier-banner-metric">
                  <span>资料完整度</span>
                  <strong>{{ supplierArchiveCompletion(selectedSupplierArchive) }}%</strong>
                </div>
              </div>

              <div class="archive-tabs" role="tablist" aria-label="供应商归档资料">
                <button
                  v-for="tab in supplierArchiveTabs"
                  :key="tab.value"
                  class="archive-tab"
                  :class="{ active: supplierArchiveTab === tab.value }"
                  @click="supplierArchiveTab = tab.value"
                >
                  {{ tab.label }} <em>{{ tab.count }}</em>
                </button>
              </div>

              <div
                v-if="supplierArchiveTab !== 'ORDERS' && canBatchUploadSelectedDocument"
                class="archive-document-toolbar"
              >
                <span>每个文件必须绑定当前供应商的一笔订单，批量提交后仍逐单校验归属。</span>
                <el-button type="primary" @click="openBatchDocumentUpload(selectedArchiveDocumentType)">
                  批量上传{{ selectedArchiveDocumentLabel }}
                </el-button>
              </div>

              <el-table
                v-if="supplierArchiveTab === 'ORDERS'"
                :data="selectedSupplierArchive.items"
                row-key="quoteItemId"
                class="detail-table archive-table"
              >
                <el-table-column label="订单号 / 下单时间" min-width="185">
                  <template #default="{ row }">
                    <el-button link type="primary" class="order-no-link" @click="openOrderDetail(row)">{{ row.orderNo }}</el-button>
                    <div class="muted-line">{{ formatDateTime(row.orderTime) }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="中标商品" min-width="250">
                  <template #default="{ row }">
                    <div class="product-cell">
                      <strong>{{ row.itemName || "-" }}</strong>
                      <span>{{ row.specModel || "-" }} · {{ formatQty(row.qty) }} {{ row.unit || "" }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="订单金额" width="135" align="right">
                  <template #default="{ row }">{{ formatMoney(row.lineAmount) }}</template>
                </el-table-column>
                <el-table-column label="交付状态" min-width="185">
                  <template #default="{ row }">
                    <div class="status-stack">
                      <span>{{ receiptStatusLabel(row) }}</span>
                      <span v-if="row.receivedAt">{{ formatDateTime(row.receivedAt) }}</span>
                      <span v-else>待人工确认收货</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="发票" width="120">
                  <template #default="{ row }">
                    <el-tag v-if="row.latestInvoice" :type="invoiceStatusType(row.latestInvoice.status)" effect="light">
                      {{ invoiceStatusLabel(row.latestInvoice.status) }}
                    </el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="220" fixed="right">
                  <template #default="{ row }">
                    <div class="inline-actions">
                      <el-button link type="primary" @click="openOrderDetail(row)">查看订单</el-button>
                      <el-button v-if="canConfirmReceipt(row)" link type="success" @click="confirmReceipt(row)">确认收货</el-button>
                      <el-button v-if="canApplyAfterSale(row)" link type="warning" @click="openAfterSaleDialog(row)">申请售后</el-button>
                      <el-button v-if="canHandleAfterSale(row)" link type="warning" @click="goWorkbench">处理售后</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>

              <el-table
                v-else-if="supplierArchiveTab === 'INVOICES'"
                :data="selectedSupplierArchive.items"
                row-key="quoteItemId"
                class="detail-table archive-table"
              >
                <el-table-column label="订单号" min-width="175" prop="orderNo" />
                <el-table-column label="商品" min-width="240" prop="itemName" show-overflow-tooltip />
                <el-table-column label="订单金额" width="135" align="right">
                  <template #default="{ row }">{{ formatMoney(row.lineAmount) }}</template>
                </el-table-column>
                <el-table-column label="发票状态" min-width="180">
                  <template #default="{ row }">
                    <div v-if="row.latestInvoice" class="status-stack">
                      <el-tag :type="invoiceStatusType(row.latestInvoice.status)" effect="light">{{ invoiceStatusLabel(row.latestInvoice.status) }}</el-tag>
                      <span>第 {{ row.latestInvoice.versionNo }} 版 · {{ row.latestInvoice.attachmentCount || 0 }} 个文件</span>
                    </div>
                    <span v-else>未上传</span>
                  </template>
                </el-table-column>
                <el-table-column label="提交时间" min-width="170">
                  <template #default="{ row }">{{ formatDateTime(row.latestInvoice?.submittedAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="150" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canAccessInvoice(row)" link type="primary" @click="openInvoiceDialog(row)">
                      {{ row.latestInvoice ? "查看发票" : "上传发票" }}
                    </el-button>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>

              <el-table
                v-else
                :data="selectedSupplierArchive.items"
                row-key="quoteItemId"
                class="detail-table archive-table"
              >
                <el-table-column label="订单号" min-width="175" prop="orderNo" />
                <el-table-column label="商品" min-width="240" prop="itemName" show-overflow-tooltip />
                <el-table-column label="签收单状态" min-width="180">
                  <template #default="{ row }">
                    <div v-if="row.latestDeliveryNote" class="status-stack">
                      <el-tag :type="invoiceStatusType(row.latestDeliveryNote.status)" effect="light">
                        {{ documentStatusLabel(row.latestDeliveryNote.status, "DELIVERY_NOTE") }}
                      </el-tag>
                      <span>第 {{ row.latestDeliveryNote.versionNo }} 版 · {{ row.latestDeliveryNote.attachmentCount || 0 }} 个文件</span>
                    </div>
                    <span v-else>未上传</span>
                  </template>
                </el-table-column>
                <el-table-column label="提交时间" min-width="170">
                  <template #default="{ row }">{{ formatDateTime(row.latestDeliveryNote?.submittedAt) }}</template>
                </el-table-column>
                <el-table-column label="提交人" min-width="170">
                  <template #default="{ row }">{{ row.latestDeliveryNote?.submittedByName || "-" }}</template>
                </el-table-column>
                <el-table-column label="操作" width="150" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canAccessInvoice(row)" link type="primary" @click="openDeliveryNoteDialog(row)">
                      {{ row.latestDeliveryNote ? "查看签收单" : "上传签收单" }}
                    </el-button>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>
            </section>
          </template>

          <template v-else>
          <dl v-if="currentGroup" class="detail-summary">
            <div>
              <dt>中标单位</dt>
              <dd>{{ supplierSummary(currentGroup) }}</dd>
            </div>
            <div>
              <dt>我的商品</dt>
              <dd>{{ currentGroup.items.length }} 项</dd>
            </div>
            <div>
              <dt>采购金额</dt>
              <dd>{{ formatMoney(currentGroup.totalAmount) }}</dd>
            </div>
            <div>
              <dt>待收货</dt>
              <dd>{{ currentGroup.waitingReceiptCount }} 项</dd>
            </div>
            <div>
              <dt>已收货</dt>
              <dd>{{ currentGroup.receivedCount }} 项</dd>
            </div>
            <div>
              <dt>售后</dt>
              <dd>{{ currentGroup.afterSaleCount }} 单</dd>
            </div>
          </dl>

          <el-form :inline="true" :model="detailQuery" class="vnet-search-form detail-filters">
            <el-form-item label="供应商">
              <el-select v-model="detailQuery.supplierName" clearable placeholder="全部供应商" class="filter-select">
                <el-option v-for="item in detailSupplierOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="申请单">
              <el-select v-model="detailQuery.sourceReqNo" clearable placeholder="全部申请单" class="filter-select">
                <el-option v-for="item in detailSourceOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="detailQuery.status" clearable placeholder="全部状态" class="filter-select">
                <el-option v-for="item in detailStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="商品">
              <el-input v-model="detailQuery.keyword" clearable placeholder="商品名称 / 编码 / 订单号" class="detail-keyword" />
            </el-form-item>
          </el-form>

          <el-table :data="filteredDetailRows" row-key="quoteItemId" class="detail-table">
            <el-table-column label="商品" min-width="260">
              <template #default="{ row }">
                <div class="product-cell">
                  <strong>{{ row.itemName || "-" }}</strong>
                  <span>编码：{{ row.itemCode || "-" }}</span>
                  <span>规格：{{ row.specModel || "-" }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="中标单位" min-width="150" prop="supplierName" show-overflow-tooltip />
            <el-table-column label="来源申请" min-width="180">
              <template #default="{ row }">
                <div class="product-cell">
                  <strong>{{ sourceRequestNo(row) }}</strong>
                  <span>{{ row.sourceReqTitle || row.reqTitle || "-" }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="订单号" min-width="150">
              <template #default="{ row }">
                <el-button link type="primary" class="order-no-link" @click="openOrderDetail(row)">
                  {{ row.orderNo }}
                </el-button>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="110" align="right">
              <template #default="{ row }">{{ formatQty(row.qty) }} {{ row.unit || "" }}</template>
            </el-table-column>
            <el-table-column label="单价" width="120" align="right">
              <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="金额" width="130" align="right">
              <template #default="{ row }">{{ formatMoney(row.lineAmount) }}</template>
            </el-table-column>
            <el-table-column label="到货信息" min-width="190">
              <template #default="{ row }">
                <div class="status-stack">
                  <span>{{ receiptStatusLabel(row) }}</span>
                  <span v-if="row.receivedAt">收货 {{ formatDateTime(row.receivedAt) }}</span>
                  <span v-else-if="canConfirmReceipt(row)">待人工确认收货</span>
                  <span>交期 {{ row.leadTimeDays ?? "-" }} 天 / 需求 {{ row.needDate || "-" }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="送达地址" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.deliveryAddress || row.receivingCity || "-" }}</template>
            </el-table-column>
            <el-table-column label="售后" min-width="160">
              <template #default="{ row }">
                <div class="status-stack">
                  <span v-if="row.latestAfterSaleId">
                    {{ afterSaleTypeLabel(row.latestAfterSaleType) }} / {{ afterSaleStatusLabel(row.latestAfterSaleStatus) }}
                  </span>
                  <span v-else>-</span>
                  <span v-if="row.latestAfterSaleNo">{{ row.latestAfterSaleNo }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="订单资料" min-width="210">
              <template #default="{ row }">
                <div v-if="canAccessInvoice(row)" class="document-status-list">
                  <div class="document-status-row">
                    <span>发票</span>
                    <el-tag v-if="row.latestInvoice" :type="invoiceStatusType(row.latestInvoice.status)" effect="light">
                      {{ documentStatusLabel(row.latestInvoice.status, "INVOICE") }}
                    </el-tag>
                    <span v-else>未上传</span>
                  </div>
                  <div class="document-status-row">
                    <span>签收单</span>
                    <el-tag v-if="row.latestDeliveryNote" :type="invoiceStatusType(row.latestDeliveryNote.status)" effect="light">
                      {{ documentStatusLabel(row.latestDeliveryNote.status, "DELIVERY_NOTE") }}
                    </el-tag>
                    <span v-else>未上传</span>
                  </div>
                </div>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <div class="table-actions">
                  <el-button link type="primary" @click="openOrderDetail(row)">详情</el-button>
                  <el-button
                    v-if="canAccessInvoice(row)"
                    link
                    type="primary"
                    @click="openInvoiceDialog(row)"
                  >
                    订单资料
                  </el-button>
                  <el-button
                    v-if="canConfirmReceipt(row)"
                    link
                    type="success"
                    @click="confirmReceipt(row)"
                  >
                    确认收货
                  </el-button>
                  <el-button
                    v-if="canApplyAfterSale(row)"
                    link
                    type="warning"
                    @click="openAfterSaleDialog(row)"
                  >
                    申请售后
                  </el-button>
                  <el-button
                    v-if="canHandleAfterSale(row)"
                    link
                    type="warning"
                    @click="goWorkbench"
                  >
                    去工作台
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          </template>
        </div>
      </VnetPanel>
    </template>

    <el-dialog
      v-model="detailVisible"
      width="min(1120px, calc(100vw - 32px))"
      top="4vh"
      class="order-cockpit-dialog"
      destroy-on-close
    >
      <template #header>
        <div v-if="currentOrder" class="order-cockpit-title">
          <div>
            <span>订单详情</span>
            <strong>{{ currentOrder.orderNo }}</strong>
          </div>
          <el-tag :type="orderStatusType(currentOrder.orderStatus)" effect="light">
            {{ orderStatusLabel(currentOrder.orderStatus) }}
          </el-tag>
        </div>
      </template>

      <div v-if="currentOrder" class="order-cockpit">
        <section class="order-command-bar">
          <dl>
            <div><dt>中标单位</dt><dd>{{ currentOrder.supplierName || "-" }}</dd></div>
            <div><dt>当前责任方</dt><dd>{{ orderOwnerText(currentOrder) }}</dd></div>
            <div><dt>下单时间</dt><dd>{{ formatDateTime(currentOrder.orderTime) }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ latestOrderUpdateTime(currentOrder) }}</dd></div>
          </dl>
          <el-button @click="copyOrderNumber(currentOrder.orderNo)">复制订单号</el-button>
        </section>

        <ol class="order-progress-track">
          <li
            v-for="step in orderProgressSteps(currentOrder)"
            :key="step.key"
            :class="`is-${step.state}`"
          >
            <span class="progress-dot">
              <el-icon v-if="step.state === 'done'"><Check /></el-icon>
              <template v-else>{{ step.index }}</template>
            </span>
            <strong>{{ step.label }}</strong>
            <small>{{ step.meta }}</small>
          </li>
        </ol>

        <nav class="order-detail-tabs" aria-label="订单详情页签">
          <button
            v-for="tab in orderDetailTabs"
            :key="tab.value"
            :class="{ active: orderDetailTab === tab.value }"
            @click="orderDetailTab = tab.value"
          >
            {{ tab.label }}
            <em v-if="tab.count != null">{{ tab.count }}</em>
          </button>
        </nav>

        <section v-if="orderDetailTab === 'ORDER'" class="order-tab-panel">
          <el-table :data="[currentOrder]" border class="order-product-table">
            <el-table-column label="商品" min-width="280">
              <template #default="{ row }">
                <div class="product-cell">
                  <strong>{{ row.itemName || "-" }}</strong>
                  <span>编码：{{ row.itemCode || "-" }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="specModel" label="规格型号" min-width="160" />
            <el-table-column label="单价" width="130" align="right">
              <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
            </el-table-column>
            <el-table-column label="数量" width="120" align="right">
              <template #default="{ row }">{{ formatQty(row.qty) }} {{ row.unit || "" }}</template>
            </el-table-column>
            <el-table-column label="小计" width="150" align="right">
              <template #default="{ row }"><strong>{{ formatMoney(row.lineAmount) }}</strong></template>
            </el-table-column>
          </el-table>
          <div class="order-total-row">
            <span>订单金额</span>
            <strong>{{ formatMoney(currentOrder.lineAmount) }}</strong>
          </div>
          <div class="order-insight-grid">
            <article>
              <h4>下一步</h4>
              <strong>{{ orderNextStep(currentOrder).title }}</strong>
              <p>{{ orderNextStep(currentOrder).meta }}</p>
            </article>
            <article>
              <h4>最新动态</h4>
              <div v-for="event in orderRecentEvents(currentOrder)" :key="`${event.label}-${event.time}`" class="order-event">
                <span></span>
                <div><strong>{{ event.label }}</strong><small>{{ event.time }}</small></div>
              </div>
            </article>
          </div>
        </section>

        <section v-else-if="orderDetailTab === 'DOCUMENTS'" class="order-tab-panel document-overview-grid">
          <article v-for="document in orderDocumentOverview(currentOrder)" :key="document.type">
            <div><strong>{{ document.label }}</strong><span>{{ document.version }}</span></div>
            <el-tag :type="document.tagType" effect="light">{{ document.status }}</el-tag>
            <el-button v-if="canAccessInvoice(currentOrder)" link type="primary" @click="openDocumentFromCockpit(document.type)">
              查看记录
            </el-button>
          </article>
        </section>

        <section v-else-if="orderDetailTab === 'AFTER_SALE'" class="order-tab-panel">
          <el-alert
            v-if="currentOrder.latestAfterSaleId"
            type="warning"
            :closable="false"
            :title="`${afterSaleTypeLabel(currentOrder.latestAfterSaleType)}：${afterSaleStatusLabel(currentOrder.latestAfterSaleStatus)}`"
            :description="afterSaleSummary(currentOrder)"
          />
          <el-empty v-else :image-size="70" description="暂无售后记录" />
        </section>

        <section v-else class="order-tab-panel source-order-grid">
          <div><span>集采/采购单号</span><strong>{{ aggregateRequestNo(currentOrder) }}</strong></div>
          <div><span>来源申请单</span><strong>{{ sourceRequestNo(currentOrder) }}</strong></div>
          <div><span>申请人</span><strong>{{ currentOrder.applicantName || "-" }}</strong></div>
          <div><span>申请标题</span><strong>{{ currentOrder.sourceReqTitle || currentOrder.reqTitle || "-" }}</strong></div>
          <div><span>送达地址</span><strong>{{ currentOrder.deliveryAddress || currentOrder.receivingCity || "-" }}</strong></div>
          <div><span>需求日期</span><strong>{{ currentOrder.needDate || "-" }}</strong></div>
        </section>
      </div>
      <template #footer>
        <el-button v-if="currentOrder?.canExportOrder" @click="exportOrder(currentOrder)">导出订单PDF</el-button>
        <el-button
          v-if="currentOrder && canAccessInvoice(currentOrder)"
          type="primary"
          @click="openInvoiceFromOrderDetail"
        >
          订单资料
        </el-button>
        <el-button
          v-if="currentOrder && canConfirmReceipt(currentOrder)"
          type="success"
          @click="confirmReceiptFromDetail"
        >
          确认收货
        </el-button>
        <el-button
          v-if="currentOrder && canApplyAfterSale(currentOrder)"
          type="warning"
          @click="openAfterSaleFromDetail"
        >
          申请售后
        </el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="batchDocumentDialogVisible"
      :title="`批量上传${batchDocumentLabel}`"
      width="min(1040px, calc(100vw - 32px))"
      destroy-on-close
      @closed="resetBatchDocumentUpload"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="`仅可为 ${selectedSupplierArchive?.name || '当前供应商'} 的订单上传${batchDocumentLabel}`"
        description="请在对应订单行选择文件；每单可选择 1-5 个文件。需要填写发票编号、金额等详细信息时，请使用列表中的单笔上传。"
      />
      <el-table :data="batchDocumentRows" row-key="quoteItemId" class="batch-document-table">
        <el-table-column label="订单号" min-width="175" prop="orderNo" />
        <el-table-column label="商品" min-width="220" prop="itemName" show-overflow-tooltip />
        <el-table-column label="当前状态" width="130">
          <template #default="{ row }">{{ batchDocumentCurrentStatus(row.order) }}</template>
        </el-table-column>
        <el-table-column :label="`${batchDocumentLabel}文件`" min-width="300">
          <template #default="{ row }">
            <el-upload
              v-model:file-list="row.files"
              multiple
              :auto-upload="false"
              accept=".pdf,.ofd,.xml,.jpg,.jpeg,.png"
              :limit="5"
              :disabled="batchDocumentSubmitting || row.status === 'SUCCESS'"
              :on-change="batchDocumentFileChangeHandler(row)"
              :on-exceed="handleBatchDocumentFileExceed"
            >
              <el-button type="primary" plain :disabled="batchDocumentSubmitting || row.status === 'SUCCESS'">
                选择文件
              </el-button>
            </el-upload>
          </template>
        </el-table-column>
        <el-table-column label="上传结果" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'SUCCESS'" type="success" effect="light">上传成功</el-tag>
            <el-tag v-else-if="row.status === 'FAILED'" type="danger" effect="light">上传失败</el-tag>
            <el-tag v-else-if="row.status === 'UPLOADING'" type="warning" effect="light">上传中</el-tag>
            <span v-else>待上传</span>
            <div v-if="row.message" class="batch-document-message">{{ row.message }}</div>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span class="batch-document-summary">已选择 {{ batchSelectedOrderCount }} 笔订单</span>
        <el-button @click="batchDocumentDialogVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="batchDocumentSubmitting"
          :disabled="batchSelectedOrderCount === 0"
          @click="submitBatchDocuments"
        >
          批量提交
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="invoiceDialogVisible"
      :title="`订单资料${currentInvoiceOrder ? ` · ${currentInvoiceOrder.orderNo}` : ''}`"
      width="900px"
      destroy-on-close
      @closed="resetInvoiceDialog"
    >
      <div v-loading="invoiceLoading" class="invoice-dialog-body">
        <el-radio-group v-model="activeDocumentType" class="document-type-switch" @change="switchDocumentType">
          <el-radio-button value="INVOICE">发票</el-radio-button>
          <el-radio-button value="DELIVERY_NOTE">签收单</el-radio-button>
        </el-radio-group>
        <el-alert
          v-if="activeDocumentLatest"
          :type="invoiceAlertType(activeDocumentLatest.status)"
          :closable="false"
          show-icon
          :title="`最新${activeDocumentLabel}：第 ${activeDocumentLatest.versionNo} 版 · ${documentStatusLabel(activeDocumentLatest.status, activeDocumentType)}`"
          :description="invoiceLatestDescription(activeDocumentLatest)"
        />

        <section v-if="canUploadActiveDocument" class="invoice-upload-section">
          <div class="invoice-section-title">
            <strong>{{ activeDocumentLatest ? `重新上传${activeDocumentLabel}` : `上传${activeDocumentLabel}` }}</strong>
            <span>每次提交形成一个新版本，历史文件会保留。</span>
          </div>
          <el-form :model="invoiceUploadForm" label-width="90px" class="invoice-upload-form">
            <div class="invoice-form-grid">
              <el-form-item :label="`${activeDocumentLabel}编号`">
                <el-input v-model="invoiceUploadForm.invoiceNo" maxlength="100" placeholder="可选" />
              </el-form-item>
              <el-form-item :label="activeDocumentType === 'INVOICE' ? '开票日期' : '送货日期'">
                <el-date-picker
                  v-model="invoiceUploadForm.invoiceDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="可选"
                  class="invoice-field"
                />
              </el-form-item>
              <el-form-item v-if="activeDocumentType === 'INVOICE'" label="发票金额">
                <el-input-number
                  v-model="invoiceUploadForm.totalAmount"
                  :min="0.01"
                  :precision="2"
                  :controls="false"
                  placeholder="可选"
                  class="invoice-field"
                />
              </el-form-item>
              <el-form-item v-if="activeDocumentType === 'INVOICE'" label="发票类型">
                <el-input v-model="invoiceUploadForm.invoiceType" maxlength="50" placeholder="如增值税专用发票，可选" />
              </el-form-item>
            </div>
            <el-form-item label="备注">
              <el-input
                v-model="invoiceUploadForm.remark"
                type="textarea"
                :rows="2"
                maxlength="500"
                show-word-limit
                :placeholder="`可填写${activeDocumentLabel}说明`"
              />
            </el-form-item>
            <el-form-item :label="`${activeDocumentLabel}文件`" required>
              <el-upload
                v-model:file-list="invoiceFiles"
                multiple
                :auto-upload="false"
                accept=".pdf,.ofd,.xml,.jpg,.jpeg,.png"
                :limit="5"
                :on-change="handleInvoiceFileChange"
                :on-exceed="handleInvoiceFileExceed"
              >
                <el-button type="primary" plain>选择文件</el-button>
                <template #tip>
                  <div class="invoice-upload-tip">请选择 1-5 个 PDF、OFD、XML、JPG、JPEG 或 PNG 文件，单个不超过 20MB。</div>
                </template>
              </el-upload>
            </el-form-item>
            <div class="invoice-upload-actions">
              <el-button type="success" :loading="invoiceSubmitting" @click="submitInvoice">提交{{ activeDocumentLabel }}</el-button>
            </div>
          </el-form>
        </section>

        <el-divider v-if="canUploadActiveDocument" />

        <section class="invoice-history-section">
          <div class="invoice-section-title">
            <strong>{{ activeDocumentLabel }}记录</strong>
            <span>按版本从新到旧排列。</span>
          </div>
          <el-empty v-if="!invoiceLoading && invoiceHistory.length === 0" :description="`暂未上传${activeDocumentLabel}`" />
          <article
            v-for="(submission, index) in invoiceHistory"
            :key="submission.submissionId"
            class="invoice-history-card"
          >
            <header>
              <div>
                <strong>第 {{ submission.versionNo }} 版</strong>
                <el-tag :type="invoiceStatusType(submission.status)" effect="light">
                  {{ invoiceHistoryStatusLabel(submission.status, index, activeDocumentType) }}
                </el-tag>
              </div>
              <span>{{ formatDateTime(submission.submittedAt) }}</span>
            </header>
            <dl class="invoice-meta-grid">
              <div><dt>{{ activeDocumentLabel }}编号</dt><dd>{{ submission.invoiceNo || "-" }}</dd></div>
              <div><dt>{{ activeDocumentType === 'INVOICE' ? '开票日期' : '送货日期' }}</dt><dd>{{ submission.invoiceDate || "-" }}</dd></div>
              <div v-if="activeDocumentType === 'INVOICE'"><dt>发票金额</dt><dd>{{ formatOptionalMoney(submission.totalAmount) }}</dd></div>
              <div v-if="activeDocumentType === 'INVOICE'"><dt>发票类型</dt><dd>{{ submission.invoiceType || "-" }}</dd></div>
              <div><dt>提交人</dt><dd>{{ submission.submittedByName || "-" }}（{{ submittedRoleLabel(submission.submittedByRole) }}）</dd></div>
              <div><dt>审核人</dt><dd>{{ submission.reviewedByName || "-" }}</dd></div>
            </dl>
            <p v-if="submission.remark" class="invoice-note">提交备注：{{ submission.remark }}</p>
            <el-alert
              v-if="submission.reviewRemark"
              class="invoice-review-alert"
              :type="submission.status === 'RETURNED' ? 'error' : 'info'"
              :closable="false"
              :title="`${submission.status === 'RETURNED' ? '退回原因' : '审核备注'}：${submission.reviewRemark}`"
            />
            <div class="invoice-attachments">
              <span>文件：</span>
              <el-button
                v-for="attachment in submission.attachments || []"
                :key="attachment.id"
                link
                type="primary"
                @click="downloadInvoiceAttachment(attachment)"
              >
                {{ attachment.fileName }}（{{ formatFileSize(attachment.fileSize) }}）
              </el-button>
            </div>
            <div
              v-if="index === 0 && submission.status === 'PENDING' && currentInvoiceOrder?.canReviewInvoice"
              class="invoice-review-actions"
            >
              <el-button type="success" :loading="invoiceReviewing" @click="acceptInvoice(submission)">确认{{ activeDocumentLabel }}</el-button>
              <el-button type="danger" plain :loading="invoiceReviewing" @click="returnInvoice(submission)">退回重传</el-button>
            </div>
          </article>
        </section>
      </div>
      <template #footer>
        <el-button @click="invoiceDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="afterSaleDialogVisible" title="申请售后" width="720px">
      <el-alert
        class="detail-alert"
        type="info"
        :closable="false"
        show-icon
        title="收货后可在7天无理由退货窗口或14天退换货窗口内申请售后，涉及更换商品需中标供应商审核后生效。"
      />
      <el-alert
        class="detail-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="afterSaleNextNodeText"
        description="提交后会进入工作台待办，审核人需要在工作台里处理，不在我的订单里直接审核。"
      />
      <el-form label-width="110px" :model="afterSaleForm">
        <el-form-item label="售后类型" required>
          <el-select v-model="afterSaleForm.afterSaleType">
            <el-option label="7天无理由退货" value="NO_REASON_RETURN" :disabled="!currentOrder?.noReasonReturnAvailable" />
            <el-option label="14天退换货" value="RETURN_EXCHANGE" :disabled="!currentOrder?.returnExchangeAvailable" />
            <el-option label="其他售后" value="OTHER" :disabled="!currentOrder?.returnExchangeAvailable" />
          </el-select>
        </el-form-item>
        <el-form-item label="售后数量" required>
          <el-input-number v-model="afterSaleForm.requestedQty" :min="1" :max="Number(currentOrder?.qty || 1)" :precision="2" />
        </el-form-item>
        <el-form-item label="售后原因" required>
          <el-input v-model="afterSaleForm.reason" type="textarea" :rows="3" placeholder="说明质量、型号、到货或使用问题" />
        </el-form-item>
        <template v-if="showReplacementFields()">
          <el-form-item label="新商品名称">
            <el-input v-model="afterSaleForm.replacementItemName" placeholder="填写更换后的商品名称" />
          </el-form-item>
          <el-form-item label="新规格型号">
            <el-input v-model="afterSaleForm.replacementSpecModel" placeholder="填写更换后的规格型号" />
          </el-form-item>
          <el-form-item label="新商品编码">
            <el-input v-model="afterSaleForm.replacementItemCode" placeholder="可选" />
          </el-form-item>
          <el-form-item label="单位">
            <el-input v-model="afterSaleForm.replacementUnit" placeholder="默认沿用原订单单位" />
          </el-form-item>
          <el-form-item label="更换说明">
            <el-input v-model="afterSaleForm.replacementRemark" type="textarea" :rows="2" placeholder="填写品牌、替换差异或补充说明" />
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="afterSaleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="afterSaleSubmitting" @click="submitAfterSale">提交售后</el-button>
      </template>
    </el-dialog>
  </VnetPage>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox, type UploadFile, type UploadFiles, type UploadUserFile } from "element-plus";
import { Check } from "@element-plus/icons-vue";
import VnetPage from "../../components/vnet/VnetPage.vue";
import VnetPanel from "../../components/vnet/VnetPanel.vue";
import {
  acceptOrderInvoiceApi,
  confirmReceiptApi,
  createAfterSaleApi,
  downloadOrderInvoiceAttachmentApi,
  exportMyOrderApi,
  exportMyOrderGroupApi,
  getMyOrderGroupApi,
  getMyOrdersPageApi,
  getOrderInvoicesApi,
  returnOrderInvoiceApi,
  uploadOrderInvoiceApi
} from "../../api/order";
import { useAuthStore } from "../../stores/auth";
import type {
  AfterSaleCreateForm,
  OrderDocumentType,
  OrderInvoiceAttachmentVO,
  OrderInvoiceStatus,
  OrderInvoiceSubmissionVO,
  OrderInvoiceSummaryVO,
  OrderInvoiceUploadForm,
  ProcurementOrderVO
} from "../../types/order";

type OrderGroupKind = "CENTRALIZED" | "URGENT" | "DIRECT";
type GroupKindFilter = "ALL" | OrderGroupKind;
type InvoiceUploadMetadata = Omit<OrderInvoiceUploadForm, "files">;
type OrderDetailTab = "ORDER" | "DOCUMENTS" | "AFTER_SALE" | "SOURCE";
type OrderProgressState = "done" | "current" | "pending";

interface OrderProgressStep {
  key: string;
  index: number;
  label: string;
  meta: string;
  state: OrderProgressState;
}

const INVOICE_FILE_EXTENSIONS = new Set(["pdf", "ofd", "xml", "jpg", "jpeg", "png"]);
const INVOICE_FILE_MAX_SIZE = 20 * 1024 * 1024;

interface OrderGroup {
  key: string;
  kind: OrderGroupKind;
  groupNo: string;
  primary: ProcurementOrderVO;
  items: ProcurementOrderVO[];
  totalAmount: number;
  latestOrderTime?: string;
  supplierNames: string[];
  sourceReqNos: string[];
  waitingReceiptCount: number;
  receivedCount: number;
  afterSaleCount: number;
  pendingAfterSaleCount: number;
}

type ArchiveLevel = "ROOMS" | "SUPPLIERS" | "SUPPLIER";
type SupplierArchiveTab = "ORDERS" | "INVOICES" | "DELIVERY_NOTES";

interface RoomArchive {
  key: string;
  name: string;
  deptName: string;
  items: ProcurementOrderVO[];
  supplierCount: number;
  sourceRequestCount: number;
  totalAmount: number;
  waitingReceiptCount: number;
  receivedCount: number;
}

interface SupplierArchive {
  key: string;
  supplierId?: number;
  name: string;
  items: ProcurementOrderVO[];
  totalAmount: number;
  waitingReceiptCount: number;
  receivedCount: number;
  invoiceUploadedCount: number;
  invoiceAcceptedCount: number;
  deliveryNoteUploadedCount: number;
  deliveryNoteAcceptedCount: number;
}

type BatchDocumentUploadStatus = "READY" | "UPLOADING" | "SUCCESS" | "FAILED";

interface BatchDocumentUploadRow {
  quoteItemId: number;
  orderNo: string;
  itemName?: string;
  order: ProcurementOrderVO;
  files: UploadUserFile[];
  status: BatchDocumentUploadStatus;
  message: string;
}

const authStore = useAuthStore();
const router = useRouter();
const loading = ref(false);
const detailLoading = ref(false);
const tableData = ref<ProcurementOrderVO[]>([]);
const activeGroupKind = ref<GroupKindFilter>("ALL");
const detailPageVisible = ref(false);
const currentGroup = ref<OrderGroup | null>(null);
const currentGroupRows = ref<ProcurementOrderVO[]>([]);
const archiveLevel = ref<ArchiveLevel>("ROOMS");
const selectedRoomKey = ref("");
const selectedSupplierKey = ref("");
const supplierArchiveTab = ref<SupplierArchiveTab>("ORDERS");
const detailVisible = ref(false);
const currentOrder = ref<ProcurementOrderVO | null>(null);
const orderDetailTab = ref<OrderDetailTab>("ORDER");
const afterSaleDialogVisible = ref(false);
const afterSaleSubmitting = ref(false);
const invoiceDialogVisible = ref(false);
const invoiceLoading = ref(false);
const invoiceSubmitting = ref(false);
const invoiceReviewing = ref(false);
const currentInvoiceOrder = ref<ProcurementOrderVO | null>(null);
const activeDocumentType = ref<OrderDocumentType>("INVOICE");
const invoiceHistory = ref<OrderInvoiceSubmissionVO[]>([]);
const invoiceFiles = ref<UploadUserFile[]>([]);
const batchDocumentDialogVisible = ref(false);
const batchDocumentSubmitting = ref(false);
const batchDocumentType = ref<OrderDocumentType>("INVOICE");
const batchDocumentRows = ref<BatchDocumentUploadRow[]>([]);

const queryModel = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: "",
  status: ""
});

const detailQuery = reactive({
  supplierName: "",
  sourceReqNo: "",
  status: "",
  keyword: ""
});

const afterSaleForm = reactive<AfterSaleCreateForm>({
  afterSaleType: "REPLACE_ITEM",
  reason: "",
  requestedQty: 1,
  replacementItemCode: "",
  replacementItemName: "",
  replacementSpecModel: "",
  replacementUnit: "",
  replacementRemark: ""
});

const invoiceUploadForm = reactive<InvoiceUploadMetadata>({
  documentType: "INVOICE",
  invoiceNo: "",
  invoiceDate: "",
  totalAmount: undefined,
  invoiceType: "",
  remark: ""
});

const activeDocumentLabel = computed(() => activeDocumentType.value === "DELIVERY_NOTE" ? "签收单" : "发票");
const orderDetailTabs = computed<Array<{ value: OrderDetailTab; label: string; count?: number }>>(() => {
  const order = currentOrder.value;
  return [
    { value: "ORDER", label: "订单", count: order ? 1 : 0 },
    { value: "DOCUMENTS", label: "订单资料", count: Number(Boolean(order?.latestInvoice)) + Number(Boolean(order?.latestDeliveryNote)) },
    { value: "AFTER_SALE", label: "售后", count: order?.latestAfterSaleId ? 1 : 0 },
    { value: "SOURCE", label: "中标与申请" }
  ];
});
const activeDocumentLatest = computed(() => {
  const order = currentInvoiceOrder.value;
  return activeDocumentType.value === "DELIVERY_NOTE" ? order?.latestDeliveryNote : order?.latestInvoice;
});
const canUploadActiveDocument = computed(() => {
  const order = currentInvoiceOrder.value;
  return activeDocumentType.value === "DELIVERY_NOTE"
    ? order?.canUploadDeliveryNote === true
    : order?.canUploadInvoice === true;
});

const isSupplierView = computed(() => {
  const userType = authStore.user?.userType?.trim().toUpperCase();
  return userType === "SUPPLIER"
    || userType === "SUPPLIER_QUOTE_VIEWER"
    || authStore.effectiveRoleCodes.some((role) => ["SUPPLIER", "SUPPLIER_QUOTE_VIEWER"].includes(role.toUpperCase()))
    || authStore.user?.supplierId != null
    || authStore.hasAnyPermission(["procurement_order:supplier-manage-all"]);
});
const canManageAllSupplierSales = computed(() =>
  authStore.hasAnyPermission(["procurement_order:supplier-manage-all"])
);
const pageTitle = computed(() => {
  if (detailPageVisible.value && currentGroup.value) {
    return `${groupKindLabel(currentGroup.value.kind)}明细`;
  }
  return isSupplierView.value ? "已售管理" : "我的订单";
});
const filterPanelTitle = computed(() => (isSupplierView.value ? "售出商品筛选" : "订单筛选"));
const listPanelTitle = computed(() => (isSupplierView.value ? "已售批次" : "订单列表（按集采单）"));
const detailPanelTitle = computed(() => (currentGroup.value ? `${groupKindLabel(currentGroup.value.kind)}明细` : "订单明细"));
const emptyDescription = computed(() => (isSupplierView.value ? "暂无已售商品" : "暂无订单"));
const keywordPlaceholder = computed(() =>
  isSupplierView.value
    ? "商品名称 / 商品编码 / 集采单号 / 订单号 / 申请人 / 供应商"
    : "商品名称 / 商品编码 / 集采单号 / 申请单号 / 供应商"
);
const orderGroups = computed<OrderGroup[]>(() => buildOrderGroups(tableData.value));
const groupKindCounts = computed<Record<GroupKindFilter, number>>(() => ({
  ALL: orderGroups.value.length,
  CENTRALIZED: orderGroups.value.filter((group) => group.kind === "CENTRALIZED").length,
  URGENT: orderGroups.value.filter((group) => group.kind === "URGENT").length,
  DIRECT: orderGroups.value.filter((group) => group.kind === "DIRECT").length
}));
const groupKindTabs = computed(() => [
  { label: "全部", value: "ALL" as GroupKindFilter, count: groupKindCounts.value.ALL },
  { label: isSupplierView.value ? "集采批次" : "集采订单", value: "CENTRALIZED" as GroupKindFilter, count: groupKindCounts.value.CENTRALIZED },
  { label: "紧急采购", value: "URGENT" as GroupKindFilter, count: groupKindCounts.value.URGENT },
  { label: "普通订单", value: "DIRECT" as GroupKindFilter, count: groupKindCounts.value.DIRECT }
]);
const filteredGroups = computed(() => (
  activeGroupKind.value === "ALL"
    ? orderGroups.value
    : orderGroups.value.filter((group) => group.kind === activeGroupKind.value)
));
const total = computed(() => filteredGroups.value.length);
const pagedGroups = computed(() => {
  const start = (queryModel.pageNo - 1) * queryModel.pageSize;
  return filteredGroups.value.slice(start, start + queryModel.pageSize);
});
const orderStatusTabs = computed(() =>
  isSupplierView.value
    ? [
        { label: "全部售出", value: "" },
        { label: "中标待确认", value: "SUPPLIER_CONFIRM_PENDING" },
        { label: "待买方收货", value: "WAITING_RECEIPT" },
        { label: "已完成", value: "COMPLETED" },
        { label: "售后待给方案", value: "AFTER_SALE_PENDING" },
        { label: "方案待确认", value: "AFTER_SALE_PROPOSAL_PENDING" },
        { label: "售后通过", value: "AFTER_SALE_APPROVED" },
        { label: "售后驳回", value: "AFTER_SALE_REJECTED" }
      ]
    : [
        { label: "全部订单", value: "" },
        { label: "待供应商确认", value: "SUPPLIER_CONFIRM_PENDING" },
        { label: "待收货", value: "WAITING_RECEIPT" },
        { label: "已完成", value: "COMPLETED" },
        { label: "售后待方案", value: "AFTER_SALE_PENDING" },
        { label: "方案待确认", value: "AFTER_SALE_PROPOSAL_PENDING" },
        { label: "售后通过", value: "AFTER_SALE_APPROVED" },
        { label: "售后驳回", value: "AFTER_SALE_REJECTED" }
      ]
);
const detailStatusOptions = computed(() => orderStatusTabs.value.filter((tab) => tab.value));
const detailSupplierOptions = computed(() => unique(currentGroupRows.value.map((order) => order.supplierName).filter(Boolean) as string[]));
const detailSourceOptions = computed(() => unique(currentGroupRows.value.map((order) => sourceRequestNo(order)).filter((item) => item !== "-")));
const filteredDetailRows = computed(() => {
  const keyword = detailQuery.keyword.trim().toLowerCase();
  return currentGroupRows.value.filter((order) => {
    if (detailQuery.supplierName && order.supplierName !== detailQuery.supplierName) {
      return false;
    }
    if (detailQuery.sourceReqNo && sourceRequestNo(order) !== detailQuery.sourceReqNo) {
      return false;
    }
    if (detailQuery.status && order.orderStatus !== detailQuery.status) {
      return false;
    }
    if (!keyword) {
      return true;
    }
    return [
      order.itemName,
      order.itemCode,
      order.specModel,
      order.orderNo,
      order.supplierName,
      sourceRequestNo(order)
    ].filter(Boolean).join(" ").toLowerCase().includes(keyword);
  });
});
const roomArchives = computed<RoomArchive[]>(() => buildRoomArchives(currentGroupRows.value));
const selectedRoomArchive = computed<RoomArchive | null>(() => (
  roomArchives.value.find((room) => room.key === selectedRoomKey.value) || null
));
const supplierArchives = computed<SupplierArchive[]>(() => buildSupplierArchives(selectedRoomArchive.value?.items || []));
const selectedSupplierArchive = computed<SupplierArchive | null>(() => (
  supplierArchives.value.find((supplier) => supplier.key === selectedSupplierKey.value) || null
));
const supplierArchiveTabs = computed(() => {
  const items = selectedSupplierArchive.value?.items || [];
  return [
    { label: "订单", value: "ORDERS" as SupplierArchiveTab, count: items.length },
    { label: "发票", value: "INVOICES" as SupplierArchiveTab, count: items.filter((item) => item.latestInvoice).length },
    { label: "签收单", value: "DELIVERY_NOTES" as SupplierArchiveTab, count: items.filter((item) => item.latestDeliveryNote).length }
  ];
});
const selectedArchiveDocumentType = computed<OrderDocumentType>(() => (
  supplierArchiveTab.value === "DELIVERY_NOTES" ? "DELIVERY_NOTE" : "INVOICE"
));
const selectedArchiveDocumentLabel = computed(() => (
  selectedArchiveDocumentType.value === "DELIVERY_NOTE" ? "签收单" : "发票"
));
const batchDocumentLabel = computed(() => (
  batchDocumentType.value === "DELIVERY_NOTE" ? "签收单" : "发票"
));
const canBatchUploadSelectedDocument = computed(() => (
  selectedSupplierArchive.value?.items.some((order) => (
    selectedArchiveDocumentType.value === "DELIVERY_NOTE"
      ? order.canUploadDeliveryNote === true
      : order.canUploadInvoice === true
  )) === true
));
const batchSelectedOrderCount = computed(() => (
  batchDocumentRows.value.filter((row) => (
    row.status !== "SUCCESS" && row.files.some((file) => Boolean(file.raw))
  )).length
));
const detailBackLabel = computed(() => {
  if (currentGroup.value?.kind !== "CENTRALIZED" || archiveLevel.value === "ROOMS") {
    return "返回订单列表";
  }
  return archiveLevel.value === "SUPPLIER" ? "返回供应商列表" : "返回机房列表";
});
const detailHeading = computed(() => {
  if (!currentGroup.value) {
    return "-";
  }
  if (currentGroup.value.kind !== "CENTRALIZED") {
    return `${groupKindLabel(currentGroup.value.kind)}：${currentGroup.value.groupNo}`;
  }
  if (archiveLevel.value === "SUPPLIER" && selectedSupplierArchive.value) {
    return `${selectedSupplierArchive.value.name} · 订单归档`;
  }
  if (archiveLevel.value === "SUPPLIERS" && selectedRoomArchive.value) {
    return `${selectedRoomArchive.value.name} · 中标供应商`;
  }
  return `集采单：${currentGroup.value.groupNo}`;
});
const detailSubtitle = computed(() => {
  if (!currentGroup.value) {
    return "";
  }
  if (currentGroup.value.kind !== "CENTRALIZED") {
    return sourceSummary(currentGroup.value);
  }
  if (archiveLevel.value === "SUPPLIER") {
    return `${selectedRoomArchive.value?.name || "-"} · 中标资料归档`;
  }
  return `${currentGroup.value.sourceReqNos.length} 张来源申请 · ${currentGroup.value.items.length} 项中标商品`;
});

const afterSaleNextReviewer = computed(() => currentOrder.value?.supplierName || "中标供应商");
const afterSaleNextNodeText = computed(() => `下一步：供应商售后审核 / 处理人：${afterSaleNextReviewer.value}`);

async function loadOrders(): Promise<void> {
  loading.value = true;
  try {
    tableData.value = await fetchAllOrders();
    queryModel.pageNo = Math.min(queryModel.pageNo, Math.max(1, Math.ceil(orderGroups.value.length / queryModel.pageSize)));
  } finally {
    loading.value = false;
  }
}

async function fetchAllOrders(): Promise<ProcurementOrderVO[]> {
  const pageSize = 500;
  const baseQuery = {
    keyword: queryModel.keyword,
    status: queryModel.status
  };
  const first = await getMyOrdersPageApi({ ...baseQuery, pageNo: 1, pageSize });
  const records = [...(first.records || [])];
  const totalRows = first.total || records.length;
  for (let pageNo = 2; records.length < totalRows; pageNo += 1) {
    const data = await getMyOrdersPageApi({ ...baseQuery, pageNo, pageSize });
    const pageRecords = data.records || [];
    if (pageRecords.length === 0) {
      break;
    }
    records.push(...pageRecords);
  }
  return records;
}

function loadAll(): void {
  if (detailPageVisible.value && currentGroup.value) {
    loadGroupDetail(currentGroup.value.groupNo);
    return;
  }
  loadOrders();
}

function switchStatus(status: string): void {
  queryModel.status = status;
  queryModel.pageNo = 1;
  loadOrders();
}

function handleSearch(): void {
  queryModel.pageNo = 1;
  loadOrders();
}

function resetQuery(): void {
  queryModel.keyword = "";
  queryModel.status = "";
  queryModel.pageNo = 1;
  loadOrders();
}

function handlePageChange(pageNo: number): void {
  queryModel.pageNo = pageNo;
}

function handleSizeChange(pageSize: number): void {
  queryModel.pageSize = pageSize;
  queryModel.pageNo = 1;
}

function switchGroupKind(kind: GroupKindFilter): void {
  activeGroupKind.value = kind;
  queryModel.pageNo = 1;
}

async function openGroupDetail(group: OrderGroup): Promise<void> {
  currentGroup.value = group;
  currentGroupRows.value = group.items;
  detailPageVisible.value = true;
  archiveLevel.value = "ROOMS";
  selectedRoomKey.value = "";
  selectedSupplierKey.value = "";
  supplierArchiveTab.value = "ORDERS";
  resetDetailQuery();
  await loadGroupDetail(group.groupNo);
}

async function loadGroupDetail(groupNo: string): Promise<void> {
  detailLoading.value = true;
  try {
    const rows = await getMyOrderGroupApi(groupNo);
    currentGroupRows.value = rows || [];
    const groups = buildOrderGroups(currentGroupRows.value);
    currentGroup.value = groups.find((group) => group.groupNo === groupNo) || groups[0] || currentGroup.value;
    restoreArchiveSelection();
  } finally {
    detailLoading.value = false;
  }
}

function backToList(): void {
  detailPageVisible.value = false;
  currentGroup.value = null;
  currentGroupRows.value = [];
  archiveLevel.value = "ROOMS";
  selectedRoomKey.value = "";
  selectedSupplierKey.value = "";
  supplierArchiveTab.value = "ORDERS";
  resetDetailQuery();
}

function handleDetailBack(): void {
  if (currentGroup.value?.kind !== "CENTRALIZED" || archiveLevel.value === "ROOMS") {
    backToList();
    return;
  }
  if (archiveLevel.value === "SUPPLIER") {
    archiveLevel.value = "SUPPLIERS";
    selectedSupplierKey.value = "";
    supplierArchiveTab.value = "ORDERS";
    return;
  }
  archiveLevel.value = "ROOMS";
  selectedRoomKey.value = "";
}

function openRoomArchive(room: RoomArchive): void {
  selectedRoomKey.value = room.key;
  selectedSupplierKey.value = "";
  archiveLevel.value = "SUPPLIERS";
}

function openSupplierArchive(supplier: SupplierArchive): void {
  selectedSupplierKey.value = supplier.key;
  supplierArchiveTab.value = "ORDERS";
  archiveLevel.value = "SUPPLIER";
}

function restoreArchiveSelection(): void {
  if (currentGroup.value?.kind !== "CENTRALIZED") {
    return;
  }
  if (selectedRoomKey.value && !roomArchives.value.some((room) => room.key === selectedRoomKey.value)) {
    archiveLevel.value = "ROOMS";
    selectedRoomKey.value = "";
    selectedSupplierKey.value = "";
    return;
  }
  if (selectedSupplierKey.value && !supplierArchives.value.some((supplier) => supplier.key === selectedSupplierKey.value)) {
    archiveLevel.value = "SUPPLIERS";
    selectedSupplierKey.value = "";
  }
}

function resetDetailQuery(): void {
  detailQuery.supplierName = "";
  detailQuery.sourceReqNo = "";
  detailQuery.status = "";
  detailQuery.keyword = "";
}

function openOrderDetail(order: ProcurementOrderVO): void {
  currentOrder.value = order;
  orderDetailTab.value = "ORDER";
  detailVisible.value = true;
}

async function openDocumentFromCockpit(documentType: OrderDocumentType): Promise<void> {
  const order = currentOrder.value;
  if (!order) {
    return;
  }
  detailVisible.value = false;
  await openOrderDocumentDialog(order, documentType);
}

function openBatchDocumentUpload(documentType: OrderDocumentType): void {
  const archive = selectedSupplierArchive.value;
  if (!archive) {
    return;
  }
  const eligibleOrders = archive.items.filter((order) => canUploadOrderDocument(order, documentType));
  if (eligibleOrders.length === 0) {
    ElMessage.warning(`当前账号不能为该供应商上传${documentType === "DELIVERY_NOTE" ? "签收单" : "发票"}`);
    return;
  }
  batchDocumentType.value = documentType;
  batchDocumentRows.value = eligibleOrders.map((order) => ({
    quoteItemId: order.quoteItemId,
    orderNo: order.orderNo,
    itemName: order.itemName,
    order,
    files: [],
    status: "READY",
    message: ""
  }));
  batchDocumentDialogVisible.value = true;
}

function resetBatchDocumentUpload(): void {
  if (batchDocumentSubmitting.value) {
    return;
  }
  batchDocumentRows.value = [];
  batchDocumentType.value = "INVOICE";
}

function canUploadOrderDocument(order: ProcurementOrderVO, documentType: OrderDocumentType): boolean {
  return documentType === "DELIVERY_NOTE"
    ? order.canUploadDeliveryNote === true
    : order.canUploadInvoice === true;
}

function batchDocumentCurrentStatus(order: ProcurementOrderVO): string {
  const document = batchDocumentType.value === "DELIVERY_NOTE"
    ? order.latestDeliveryNote
    : order.latestInvoice;
  return document ? documentStatusLabel(document.status, batchDocumentType.value) : "未上传";
}

function handleBatchDocumentFileChange(row: BatchDocumentUploadRow, file: UploadFile): void {
  const raw = file.raw;
  if (!raw) {
    return;
  }
  const extension = raw.name.includes(".") ? raw.name.split(".").pop()?.toLowerCase() : "";
  if (!extension || !INVOICE_FILE_EXTENSIONS.has(extension)) {
    ElMessage.warning("仅支持 PDF、OFD、XML、JPG、JPEG 或 PNG 文件");
    removeBatchDocumentFile(row, file.uid);
    return;
  }
  if (raw.size > INVOICE_FILE_MAX_SIZE) {
    ElMessage.warning(`文件 ${raw.name} 超过 20MB`);
    removeBatchDocumentFile(row, file.uid);
    return;
  }
  if (row.status === "FAILED") {
    row.status = "READY";
    row.message = "";
  }
}

function batchDocumentFileChangeHandler(row: BatchDocumentUploadRow): (file: UploadFile) => void {
  return (file) => handleBatchDocumentFileChange(row, file);
}

function handleBatchDocumentFileExceed(_files: File[], _uploadFiles: UploadFiles): void {
  ElMessage.warning(`每笔订单最多上传 5 个${batchDocumentLabel.value}文件`);
}

function removeBatchDocumentFile(row: BatchDocumentUploadRow, uid: number): void {
  row.files = row.files.filter((item) => item.uid !== uid);
}

async function submitBatchDocuments(): Promise<void> {
  const pendingRows = batchDocumentRows.value.filter((row) => (
    row.status !== "SUCCESS" && row.files.some((file) => Boolean(file.raw))
  ));
  if (pendingRows.length === 0) {
    ElMessage.warning(`请至少为一笔订单选择${batchDocumentLabel.value}文件`);
    return;
  }
  batchDocumentSubmitting.value = true;
  let successCount = 0;
  let failedCount = 0;
  try {
    for (const row of pendingRows) {
      const files = row.files.map((file) => file.raw).filter(Boolean) as File[];
      row.status = "UPLOADING";
      row.message = "";
      try {
        await uploadOrderInvoiceApi(row.quoteItemId, {
          documentType: batchDocumentType.value,
          files
        });
        row.status = "SUCCESS";
        row.message = `${batchDocumentLabel.value}已提交`;
        successCount += 1;
      } catch (error) {
        row.status = "FAILED";
        row.message = batchDocumentErrorMessage(error);
        failedCount += 1;
      }
    }
    if (successCount > 0) {
      try {
        await refreshVisibleOrders();
        for (const row of batchDocumentRows.value) {
          const refreshed = currentGroupRows.value.find((order) => order.quoteItemId === row.quoteItemId)
            || tableData.value.find((order) => order.quoteItemId === row.quoteItemId);
          if (refreshed) {
            row.order = refreshed;
          }
        }
      } catch {
        ElMessage.warning("上传已完成，但列表刷新失败，请稍后手动刷新");
      }
    }
    if (failedCount === 0) {
      ElMessage.success(`已批量提交 ${successCount} 笔${batchDocumentLabel.value}`);
    } else {
      ElMessage.warning(`批量上传完成：成功 ${successCount} 笔，失败 ${failedCount} 笔`);
    }
  } finally {
    batchDocumentSubmitting.value = false;
  }
}

function batchDocumentErrorMessage(error: unknown): string {
  if (error && typeof error === "object") {
    const requestError = error as {
      message?: string;
      response?: { data?: { message?: string } };
    };
    return requestError.response?.data?.message || requestError.message || "上传失败";
  }
  return "上传失败";
}

async function openInvoiceDialog(order: ProcurementOrderVO): Promise<void> {
  await openOrderDocumentDialog(order, "INVOICE");
}

async function openDeliveryNoteDialog(order: ProcurementOrderVO): Promise<void> {
  await openOrderDocumentDialog(order, "DELIVERY_NOTE");
}

async function openOrderDocumentDialog(order: ProcurementOrderVO, documentType: OrderDocumentType): Promise<void> {
  if (!canAccessInvoice(order)) {
    return;
  }
  currentInvoiceOrder.value = order;
  activeDocumentType.value = documentType;
  invoiceDialogVisible.value = true;
  resetInvoiceUpload();
  await loadInvoiceHistory();
}

function canAccessInvoice(order: ProcurementOrderVO): boolean {
  return order.canViewOrderDocuments === true
    || order.canViewInvoice === true
    || order.canUploadInvoice === true
    || order.canUploadDeliveryNote === true
    || order.canReviewOrderDocuments === true
    || order.canReviewInvoice === true;
}

async function openInvoiceFromOrderDetail(): Promise<void> {
  if (!currentOrder.value) {
    return;
  }
  const order = currentOrder.value;
  detailVisible.value = false;
  await openInvoiceDialog(order);
}

async function loadInvoiceHistory(): Promise<void> {
  if (!currentInvoiceOrder.value) {
    invoiceHistory.value = [];
    return;
  }
  invoiceLoading.value = true;
  try {
    invoiceHistory.value = await getOrderInvoicesApi(currentInvoiceOrder.value.quoteItemId, activeDocumentType.value);
  } finally {
    invoiceLoading.value = false;
  }
}

function resetInvoiceDialog(): void {
  currentInvoiceOrder.value = null;
  activeDocumentType.value = "INVOICE";
  invoiceHistory.value = [];
  resetInvoiceUpload();
}

function resetInvoiceUpload(): void {
  invoiceUploadForm.documentType = activeDocumentType.value;
  invoiceUploadForm.invoiceNo = "";
  invoiceUploadForm.invoiceDate = "";
  invoiceUploadForm.totalAmount = undefined;
  invoiceUploadForm.invoiceType = "";
  invoiceUploadForm.remark = "";
  invoiceFiles.value = [];
}

async function switchDocumentType(value: string | number | boolean | undefined): Promise<void> {
  activeDocumentType.value = value === "DELIVERY_NOTE" ? "DELIVERY_NOTE" : "INVOICE";
  resetInvoiceUpload();
  await loadInvoiceHistory();
}

function handleInvoiceFileChange(file: UploadFile): void {
  const raw = file.raw;
  if (!raw) {
    return;
  }
  const extension = raw.name.includes(".") ? raw.name.split(".").pop()?.toLowerCase() : "";
  if (!extension || !INVOICE_FILE_EXTENSIONS.has(extension)) {
    ElMessage.warning("仅支持 PDF、OFD、XML、JPG、JPEG 或 PNG 文件");
    removeInvoiceFile(file.uid);
    return;
  }
  if (raw.size > INVOICE_FILE_MAX_SIZE) {
    ElMessage.warning(`文件 ${raw.name} 超过 20MB`);
    removeInvoiceFile(file.uid);
  }
}

function handleInvoiceFileExceed(_files: File[], _uploadFiles: UploadFiles): void {
  ElMessage.warning(`每次最多上传 5 个${activeDocumentLabel.value}文件`);
}

function removeInvoiceFile(uid: number): void {
  invoiceFiles.value = invoiceFiles.value.filter((item) => item.uid !== uid);
}

async function submitInvoice(): Promise<void> {
  const order = currentInvoiceOrder.value;
  if (!order || !canUploadActiveDocument.value) {
    return;
  }
  const files = invoiceFiles.value.map((file) => file.raw).filter(Boolean) as File[];
  if (files.length < 1 || files.length > 5) {
    ElMessage.warning(`请选择 1-5 个${activeDocumentLabel.value}文件`);
    return;
  }
  if (files.some((file) => file.size > INVOICE_FILE_MAX_SIZE)) {
    ElMessage.warning(`单个${activeDocumentLabel.value}文件不能超过 20MB`);
    return;
  }
  invoiceSubmitting.value = true;
  try {
    await uploadOrderInvoiceApi(order.quoteItemId, {
      documentType: activeDocumentType.value,
      files,
      invoiceNo: invoiceUploadForm.invoiceNo?.trim(),
      invoiceDate: invoiceUploadForm.invoiceDate,
      totalAmount: invoiceUploadForm.totalAmount,
      invoiceType: invoiceUploadForm.invoiceType?.trim(),
      remark: invoiceUploadForm.remark?.trim()
    });
    ElMessage.success(`${activeDocumentLabel.value}已提交，等待确认`);
    resetInvoiceUpload();
    await refreshInvoiceData();
  } finally {
    invoiceSubmitting.value = false;
  }
}

async function acceptInvoice(submission: OrderInvoiceSubmissionVO): Promise<void> {
  const order = currentInvoiceOrder.value;
  if (!order?.canReviewInvoice || submission.status !== "PENDING") {
    return;
  }
  let remark = "";
  try {
    const { value } = await ElMessageBox.prompt(
      `确认已收到并核对这版${activeDocumentLabel.value}？`,
      `确认${activeDocumentLabel.value}`,
      {
      confirmButtonText: `确认${activeDocumentLabel.value}`,
      cancelButtonText: "取消",
      inputPlaceholder: "确认备注，可不填",
      type: "success"
      }
    );
    remark = value?.trim() || "";
  } catch {
    return;
  }
  invoiceReviewing.value = true;
  try {
    await acceptOrderInvoiceApi(order.quoteItemId, submission.submissionId, { remark });
    ElMessage.success(`已确认${activeDocumentLabel.value}`);
    await refreshInvoiceData();
  } finally {
    invoiceReviewing.value = false;
  }
}

async function returnInvoice(submission: OrderInvoiceSubmissionVO): Promise<void> {
  const order = currentInvoiceOrder.value;
  if (!order?.canReviewInvoice || submission.status !== "PENDING") {
    return;
  }
  let remark = "";
  try {
    const { value } = await ElMessageBox.prompt("请说明需要供应商重新上传的原因。", "退回重传", {
      confirmButtonText: "确认退回",
      cancelButtonText: "取消",
      inputPlaceholder: "请填写退回原因",
      inputValidator: (input) => input.trim().length > 0 || "请填写退回原因",
      type: "warning"
    });
    remark = value.trim();
  } catch {
    return;
  }
  invoiceReviewing.value = true;
  try {
    await returnOrderInvoiceApi(order.quoteItemId, submission.submissionId, { remark });
    ElMessage.success(`已退回${activeDocumentLabel.value}，等待重新上传`);
    await refreshInvoiceData();
  } finally {
    invoiceReviewing.value = false;
  }
}

async function downloadInvoiceAttachment(attachment: OrderInvoiceAttachmentVO): Promise<void> {
  const order = currentInvoiceOrder.value;
  if (!order || !canAccessInvoice(order)) {
    return;
  }
  const blob = await downloadOrderInvoiceAttachmentApi(order.quoteItemId, attachment.id);
  downloadBlob(blob, safeFileName(attachment.fileName || `${activeDocumentLabel.value}附件-${attachment.id}`));
}

async function refreshInvoiceData(): Promise<void> {
  const order = currentInvoiceOrder.value;
  if (!order) {
    return;
  }
  const quoteItemId = order.quoteItemId;
  const [history] = await Promise.all([
    getOrderInvoicesApi(quoteItemId, activeDocumentType.value),
    refreshVisibleOrders()
  ]);
  invoiceHistory.value = history;
  const refreshedOrder = currentGroupRows.value.find((item) => item.quoteItemId === quoteItemId)
    || tableData.value.find((item) => item.quoteItemId === quoteItemId);
  if (refreshedOrder) {
    currentInvoiceOrder.value = refreshedOrder;
    if (currentOrder.value?.quoteItemId === quoteItemId) {
      currentOrder.value = refreshedOrder;
    }
  }
}

async function refreshVisibleOrders(): Promise<void> {
  if (detailPageVisible.value && currentGroup.value) {
    await loadGroupDetail(currentGroup.value.groupNo);
  } else {
    await loadOrders();
  }
}

function openAfterSaleDialog(order: ProcurementOrderVO): void {
  if (!canApplyAfterSale(order)) {
    return;
  }
  currentOrder.value = order;
  afterSaleForm.afterSaleType = order.noReasonReturnAvailable ? "NO_REASON_RETURN" : "RETURN_EXCHANGE";
  afterSaleForm.reason = "";
  afterSaleForm.requestedQty = 1;
  afterSaleForm.replacementItemCode = "";
  afterSaleForm.replacementItemName = "";
  afterSaleForm.replacementSpecModel = "";
  afterSaleForm.replacementUnit = order.unit || "";
  afterSaleForm.replacementRemark = "";
  afterSaleDialogVisible.value = true;
}

function openAfterSaleFromDetail(): void {
  if (!currentOrder.value) {
    return;
  }
  const order = currentOrder.value;
  detailVisible.value = false;
  openAfterSaleDialog(order);
}

function canConfirmReceipt(order: ProcurementOrderVO): boolean {
  return order.canConfirmReceipt === true;
}

function canApplyAfterSale(order: ProcurementOrderVO): boolean {
  return order.canApplyAfterSale === true && !hasActiveAfterSale(order);
}

function canExportOrderGroup(group: OrderGroup): boolean {
  return group.items.some((order) => order.canExportOrder === true);
}

function canHandleAfterSale(order: ProcurementOrderVO): boolean {
  return (isSupplierView.value && order.latestAfterSaleStatus === "PENDING")
    || (!isSupplierView.value && order.canConfirmAfterSaleProposal === true);
}

function hasActiveAfterSale(order: ProcurementOrderVO): boolean {
  return ["PENDING", "PROPOSAL_PENDING", "APPROVED"].includes(order.latestAfterSaleStatus || "");
}

async function goWorkbench(): Promise<void> {
  await router.push("/workbench");
}

async function confirmReceipt(order: ProcurementOrderVO): Promise<boolean> {
  if (!canConfirmReceipt(order)) {
    return false;
  }
  let remark = "";
  try {
    const { value } = await ElMessageBox.prompt(`确认已收到订单 ${order.orderNo} 的商品？`, "确认收货", {
      confirmButtonText: "确认收货",
      cancelButtonText: "取消",
      inputPlaceholder: "收货备注，可不填",
      type: "success"
    });
    remark = value || "";
  } catch {
    return false;
  }
  await confirmReceiptApi(order.quoteItemId, { receiptRemark: remark });
  ElMessage.success("已确认收货");
  loadAll();
  return true;
}

async function confirmReceiptFromDetail(): Promise<void> {
  if (!currentOrder.value) {
    return;
  }
  const confirmed = await confirmReceipt(currentOrder.value);
  if (confirmed) {
    detailVisible.value = false;
  }
}

async function exportOrder(order: ProcurementOrderVO): Promise<void> {
  if (!order.canExportOrder) {
    return;
  }
  const blob = await exportMyOrderApi(order.quoteItemId);
  downloadBlob(blob, `操作订单-${safeFileName(order.orderNo || String(order.quoteItemId))}.pdf`);
}

async function exportOrderGroup(group: OrderGroup): Promise<void> {
  if (!canExportOrderGroup(group)) {
    return;
  }
  const blob = await exportMyOrderGroupApi(group.groupNo);
  downloadBlob(blob, `操作订单-${safeFileName(group.groupNo)}.pdf`);
}

async function exportSupplierArchive(supplier: SupplierArchive): Promise<void> {
  const group = currentGroup.value;
  const room = selectedRoomArchive.value;
  if (!group || !room || !canExportSupplierArchive(supplier)) {
    return;
  }
  const blob = await exportMyOrderGroupApi(group.groupNo, {
    deptName: room.deptName,
    supplierId: supplier.supplierId
  });
  downloadBlob(blob, `操作订单-${safeFileName(group.groupNo)}-${safeFileName(room.name)}-${safeFileName(supplier.name)}.pdf`);
}

function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

async function submitAfterSale(): Promise<void> {
  if (!currentOrder.value) {
    return;
  }
  if (!afterSaleForm.reason?.trim()) {
    ElMessage.warning("请填写售后原因");
    return;
  }
  if (afterSaleForm.afterSaleType === "REPLACE_ITEM" && !afterSaleForm.replacementItemName?.trim()) {
    ElMessage.warning("请填写更换后的商品名称");
    return;
  }
  const confirmed = await ElMessageBox.confirm(
    `确认提交售后申请？${afterSaleNextNodeText.value}`,
    "提交售后确认",
    {
      type: "warning",
      confirmButtonText: "确认提交",
      cancelButtonText: "取消"
    }
  )
    .then(() => true)
    .catch(() => false);
  if (!confirmed) {
    return;
  }
  afterSaleSubmitting.value = true;
  try {
    await createAfterSaleApi(currentOrder.value.quoteItemId, { ...afterSaleForm });
    ElMessage.success(`售后申请已提交至${afterSaleNextReviewer.value}`);
    afterSaleDialogVisible.value = false;
    loadAll();
  } finally {
    afterSaleSubmitting.value = false;
  }
}

function buildOrderGroups(rows: ProcurementOrderVO[]): OrderGroup[] {
  const map = new Map<string, ProcurementOrderVO[]>();
  for (const order of rows) {
    const kind = resolveGroupKind(order);
    const groupNo = aggregateRequestNo(order) || order.orderNo || `ORDER-${order.quoteItemId}`;
    const key = `${kind}:${groupNo}`;
    const groupRows = map.get(key);
    if (groupRows) {
      groupRows.push(order);
    } else {
      map.set(key, [order]);
    }
  }
  return [...map.entries()]
    .map(([key, items]) => createOrderGroup(key, items))
    .sort((a, b) => (b.latestOrderTime || "").localeCompare(a.latestOrderTime || ""));
}

function createOrderGroup(key: string, items: ProcurementOrderVO[]): OrderGroup {
  const primary = items[0];
  const kind = resolveGroupKind(primary);
  const groupNo = aggregateRequestNo(primary) || primary.orderNo || key;
  return {
    key,
    kind,
    groupNo,
    primary,
    items,
    totalAmount: items.reduce((sum, order) => sum + Number(order.lineAmount || 0), 0),
    latestOrderTime: latestOrderTime(items),
    supplierNames: unique(items.map((order) => order.supplierName).filter(Boolean) as string[]),
    sourceReqNos: unique(items.map((order) => sourceRequestNo(order)).filter((item) => item !== "-")),
    waitingReceiptCount: items.filter((order) => order.orderStatus === "WAITING_RECEIPT").length,
    receivedCount: items.filter((order) => order.receiptStatus === "RECEIVED").length,
    afterSaleCount: items.filter((order) => order.latestAfterSaleId).length,
    pendingAfterSaleCount: items.filter((order) => ["PENDING", "PROPOSAL_PENDING"].includes(order.latestAfterSaleStatus || "")).length
  };
}

function latestOrderTime(items: ProcurementOrderVO[]): string | undefined {
  const values = items.map((order) => order.orderTime || "").filter(Boolean).sort();
  return values.length > 0 ? values[values.length - 1] : undefined;
}

function resolveGroupKind(order: ProcurementOrderVO): OrderGroupKind {
  const aggregateNo = aggregateRequestNo(order);
  const sourceNo = sourceRequestNo(order);
  if (aggregateNo.startsWith("HZCG-") || (aggregateNo !== sourceNo && aggregateNo !== "-")) {
    return "CENTRALIZED";
  }
  if (isUrgentOrder(order)) {
    return "URGENT";
  }
  return "DIRECT";
}

function isUrgentOrder(order: ProcurementOrderVO): boolean {
  const text = [
    order.reqNo,
    order.sourceReqNo,
    order.reqTitle,
    order.sourceReqTitle,
    order.reqType,
    order.sourceReqType
  ].filter(Boolean).join(" ");
  return text.includes("JJCG-") || text.includes("紧急");
}

function groupKindLabel(kind: OrderGroupKind): string {
  if (kind === "CENTRALIZED") {
    return "集采单";
  }
  if (kind === "URGENT") {
    return "紧急采购";
  }
  return "采购单";
}

function groupKindTagType(kind: OrderGroupKind): "success" | "warning" | "primary" | "info" {
  if (kind === "CENTRALIZED") {
    return "primary";
  }
  if (kind === "URGENT") {
    return "warning";
  }
  return "info";
}

function groupStatusLabel(group: OrderGroup): string {
  if (group.items.some((order) => order.orderStatus === "SUPPLIER_CONFIRM_PENDING")) {
    return "待供应商确认";
  }
  if (group.waitingReceiptCount > 0) {
    return "待收货";
  }
  if (group.pendingAfterSaleCount > 0) {
    return isSupplierView.value ? "售后待处理" : "售后处理中";
  }
  if (group.afterSaleCount > 0) {
    return "售后完成";
  }
  return "已完成";
}

function groupStatusType(group: OrderGroup): "success" | "warning" | "danger" | "info" {
  if (group.items.some((order) => order.orderStatus === "SUPPLIER_CONFIRM_PENDING") || group.waitingReceiptCount > 0 || group.pendingAfterSaleCount > 0) {
    return "warning";
  }
  if (group.items.some((order) => order.orderStatus === "AFTER_SALE_REJECTED")) {
    return "danger";
  }
  if (group.items.some((order) => order.orderStatus === "AFTER_SALE_APPROVED") || group.afterSaleCount > 0) {
    return "success";
  }
  return "info";
}

function supplierSummary(group: OrderGroup): string {
  if (group.supplierNames.length === 0) {
    return "-";
  }
  if (group.supplierNames.length <= 2) {
    return group.supplierNames.join("、");
  }
  return `${group.supplierNames.slice(0, 2).join("、")} 等 ${group.supplierNames.length} 家`;
}

function sourceSummary(group: OrderGroup): string {
  if (group.kind === "CENTRALIZED") {
    return group.sourceReqNos.length > 0 ? `来源申请 ${group.sourceReqNos.length} 张` : "";
  }
  return group.sourceReqNos[0] ? `申请单 ${group.sourceReqNos[0]}` : "";
}

function sourceRequestNo(order: ProcurementOrderVO): string {
  return order.sourceReqNo || order.reqNo || "-";
}

function aggregateRequestNo(order: ProcurementOrderVO): string {
  return order.aggregateReqNo || order.reqNo || "-";
}

function unique(values: string[]): string[] {
  return [...new Set(values.filter((item) => item && item !== "-"))];
}

function orderStatusLabel(status?: string): string {
  const map: Record<string, string> = {
    SUPPLIER_CONFIRM_PENDING: "待供应商确认",
    SUPPLIER_CONFIRM_WAIVED: "供应商已放弃",
    WAITING_RECEIPT: "待收货",
    COMPLETED: "已完成",
    AFTER_SALE_PENDING: isSupplierView.value ? "售后待给方案" : "售后待供应商方案",
    AFTER_SALE_PROPOSAL_PENDING: "售后方案待确认",
    AFTER_SALE_APPROVED: "售后已通过",
    AFTER_SALE_REJECTED: "售后已驳回"
  };
  return map[status || ""] || "已完成";
}

function orderStatusType(status?: string): "success" | "warning" | "danger" | "info" {
  if (["SUPPLIER_CONFIRM_PENDING", "WAITING_RECEIPT", "AFTER_SALE_PENDING", "AFTER_SALE_PROPOSAL_PENDING"].includes(status || "")) {
    return "warning";
  }
  if (["SUPPLIER_CONFIRM_WAIVED", "AFTER_SALE_REJECTED"].includes(status || "")) {
    return "danger";
  }
  if (["COMPLETED", "AFTER_SALE_APPROVED"].includes(status || "")) {
    return "success";
  }
  return "info";
}

function orderOwnerText(order: ProcurementOrderVO): string {
  if (order.orderStatus === "SUPPLIER_CONFIRM_PENDING") {
    return `${order.supplierName || "中标供应商"}确认中标`;
  }
  if (order.latestAfterSaleStatus === "PENDING") {
    return `${order.supplierName || "中标供应商"}处理售后`;
  }
  if (order.latestAfterSaleStatus === "PROPOSAL_PENDING") {
    return `${order.applicantName || "申请人"}确认方案`;
  }
  if (order.receiptStatus === "RECEIVED") {
    if (paymentSubmittedAt(order)) {
      return "已提交付款";
    }
    if (paymentDocumentsSubmitted(order)) {
      return "采购方确认付款资料";
    }
    return `${order.supplierName || "中标供应商"}补齐付款资料`;
  }
  return `${order.supplierName || "中标供应商"}配送`;
}

function orderProgressSteps(order: ProcurementOrderVO): OrderProgressStep[] {
  const supplierConfirmed = order.supplierConfirmStatus === "CONFIRMED"
    || order.receiptStatus === "RECEIVED"
    || ["WAITING_RECEIPT", "COMPLETED", "AFTER_SALE_PENDING", "AFTER_SALE_PROPOSAL_PENDING", "AFTER_SALE_APPROVED"].includes(order.orderStatus || "");
  const received = order.receiptStatus === "RECEIVED";
  const supplierStepState: OrderProgressState = supplierConfirmed ? "done" : "current";
  const orderStepState: OrderProgressState = supplierConfirmed ? "done" : "pending";
  const deliveryStepState: OrderProgressState = received ? "done" : supplierConfirmed ? "current" : "pending";
  const documentsSubmitted = paymentDocumentsSubmitted(order);
  const documentsSubmittedAt = paymentDocumentsSubmittedAt(order);
  const submittedForPaymentAt = paymentSubmittedAt(order);
  const documentStepState: OrderProgressState = received ? (documentsSubmitted ? "done" : "current") : "pending";
  const paymentStepState: OrderProgressState = submittedForPaymentAt
    ? "done"
    : received && documentsSubmitted
      ? "current"
      : "pending";
  return [
    { key: "AWARD", index: 1, label: "确认中标", meta: formatDateTime(order.orderTime), state: "done" },
    { key: "SUPPLIER", index: 2, label: "供应商确认", meta: formatDateTime(order.supplierConfirmedAt), state: supplierStepState },
    { key: "ORDER", index: 3, label: "订单生成", meta: formatDateTime(order.orderTime), state: orderStepState },
    { key: "DELIVERY", index: 4, label: "配送中", meta: received ? formatDateTime(order.receivedAt) : formatDateTime(order.receiptDueAt), state: deliveryStepState },
    { key: "RECEIPT", index: 5, label: "已签收", meta: received ? formatDateTime(order.receivedAt) : "待签收", state: received ? "done" : "pending" },
    {
      key: "DOCUMENTS",
      index: 6,
      label: "发票/验收单提交",
      meta: documentsSubmittedAt ? formatDateTime(documentsSubmittedAt) : received ? "待提交" : "待签收",
      state: documentStepState
    },
    {
      key: "PAYMENT",
      index: 7,
      label: "已提交付款",
      meta: submittedForPaymentAt ? formatDateTime(submittedForPaymentAt) : documentsSubmitted ? "待资料确认" : "待提交",
      state: paymentStepState
    }
  ];
}

function orderNextStep(order: ProcurementOrderVO): { title: string; meta: string } {
  if (order.orderStatus === "SUPPLIER_CONFIRM_PENDING") {
    return { title: "等待供应商确认中标", meta: order.supplierName || "中标供应商" };
  }
  if (order.latestAfterSaleStatus === "PENDING") {
    return { title: "等待供应商给出售后方案", meta: order.latestAfterSaleNo || "-" };
  }
  if (order.latestAfterSaleStatus === "PROPOSAL_PENDING") {
    return { title: "确认供应商售后方案", meta: order.latestAfterSaleNo || "-" };
  }
  if (order.receiptStatus === "RECEIVED") {
    const submittedForPaymentAt = paymentSubmittedAt(order);
    if (submittedForPaymentAt) {
      return { title: "已提交付款", meta: formatDateTime(submittedForPaymentAt) };
    }
    if (paymentDocumentsSubmitted(order)) {
      return { title: "等待发票和验收单确认", meta: "资料确认后提交付款" };
    }
    return { title: "提交发票和验收单", meta: "两类付款资料均需有效提交" };
  }
  return { title: "等待到货并确认收货", meta: order.receiptDueAt ? `预计 ${formatDateTime(order.receiptDueAt)} 前` : `交期 ${order.leadTimeDays ?? "-"} 天` };
}

function orderRecentEvents(order: ProcurementOrderVO): Array<{ label: string; time: string }> {
  const submittedForPaymentAt = paymentSubmittedAt(order);
  const events = [
    submittedForPaymentAt ? { label: "发票和验收单已确认，已提交付款", time: formatDateTime(submittedForPaymentAt) } : null,
    order.receivedAt ? { label: "已确认收货", time: formatDateTime(order.receivedAt) } : null,
    order.latestDeliveryNote?.submittedAt ? { label: "供应商已提交验收单", time: formatDateTime(order.latestDeliveryNote.submittedAt) } : null,
    order.latestInvoice?.submittedAt ? { label: "供应商已提交发票", time: formatDateTime(order.latestInvoice.submittedAt) } : null,
    order.supplierConfirmedAt ? { label: "供应商已确认中标", time: formatDateTime(order.supplierConfirmedAt) } : null,
    order.orderTime ? { label: "订单已生成", time: formatDateTime(order.orderTime) } : null
  ].filter((event): event is { label: string; time: string } => event !== null);
  return events
    .sort((left, right) => right.time.localeCompare(left.time))
    .slice(0, 4);
}

function latestOrderUpdateTime(order: ProcurementOrderVO): string {
  const times = [
    order.receivedAt,
    paymentSubmittedAt(order),
    order.latestDeliveryNote?.submittedAt,
    order.latestInvoice?.submittedAt,
    order.supplierConfirmedAt,
    order.orderTime
  ].filter((value): value is string => Boolean(value));
  const sortedTimes = times.sort();
  return sortedTimes.length > 0 ? formatDateTime(sortedTimes[sortedTimes.length - 1]) : "-";
}

function paymentDocumentsSubmitted(order: ProcurementOrderVO): boolean {
  return isEffectivePaymentDocument(order.latestInvoice) && isEffectivePaymentDocument(order.latestDeliveryNote);
}

function isEffectivePaymentDocument(document?: OrderInvoiceSummaryVO): boolean {
  return document?.status === "PENDING" || document?.status === "ACCEPTED";
}

function paymentDocumentsSubmittedAt(order: ProcurementOrderVO): string | undefined {
  if (!paymentDocumentsSubmitted(order)) {
    return undefined;
  }
  return latestTimestamp(order.latestInvoice?.submittedAt, order.latestDeliveryNote?.submittedAt);
}

function paymentSubmittedAt(order: ProcurementOrderVO): string | undefined {
  if (order.receiptStatus !== "RECEIVED"
    || order.latestInvoice?.status !== "ACCEPTED"
    || order.latestDeliveryNote?.status !== "ACCEPTED") {
    return undefined;
  }
  return latestTimestamp(
    order.latestInvoice.reviewedAt || order.latestInvoice.submittedAt,
    order.latestDeliveryNote.reviewedAt || order.latestDeliveryNote.submittedAt
  );
}

function latestTimestamp(...values: Array<string | undefined>): string | undefined {
  const timestamps = values.filter((value): value is string => Boolean(value)).sort();
  return timestamps[timestamps.length - 1];
}

function orderDocumentOverview(order: ProcurementOrderVO): Array<{
  type: OrderDocumentType;
  label: string;
  version: string;
  status: string;
  tagType: "success" | "warning" | "danger" | "info";
}> {
  return [
    documentOverviewItem("INVOICE", "发票", order.latestInvoice),
    documentOverviewItem("DELIVERY_NOTE", "签收单", order.latestDeliveryNote)
  ];
}

function documentOverviewItem(
  type: OrderDocumentType,
  label: string,
  document?: OrderInvoiceSummaryVO
): {
  type: OrderDocumentType;
  label: string;
  version: string;
  status: string;
  tagType: "success" | "warning" | "danger" | "info";
} {
  return {
    type,
    label,
    version: document ? `第 ${document.versionNo} 版` : "未上传",
    status: document ? documentStatusLabel(document.status, type) : "未上传",
    tagType: document ? invoiceStatusType(document.status) : "info"
  };
}

async function copyOrderNumber(orderNo: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(orderNo);
    ElMessage.success("订单号已复制");
  } catch {
    ElMessage.warning("复制失败，请手动选择订单号");
  }
}

function afterSaleStatusLabel(status?: string): string {
  const map: Record<string, string> = {
    PENDING: isSupplierView.value ? "待给方案" : "待供应商方案",
    PROPOSAL_PENDING: "方案待确认",
    APPROVED: "已通过",
    REJECTED: "已驳回"
  };
  return map[status || ""] || "-";
}

function afterSaleTypeLabel(type?: string): string {
  const map: Record<string, string> = {
    NO_REASON_RETURN: "7天无理由退货",
    RETURN_EXCHANGE: "14天退换货",
    REPLACE_ITEM: "更换商品",
    REPAIR: "维修/退换",
    OTHER: "其他售后"
  };
  return map[type || ""] || "售后";
}

function buildRoomArchives(rows: ProcurementOrderVO[]): RoomArchive[] {
  const grouped = new Map<string, ProcurementOrderVO[]>();
  for (const order of rows) {
    const deptName = archiveRoomDeptName(order);
    const key = deptName;
    const items = grouped.get(key);
    if (items) {
      items.push(order);
    } else {
      grouped.set(key, [order]);
    }
  }
  return [...grouped.entries()]
    .map(([key, items]) => ({
      key,
      name: archiveRoomDisplayName(key),
      deptName: key,
      items,
      supplierCount: unique(items.map((item) => supplierArchiveKey(item))).length,
      sourceRequestCount: unique(items.map((item) => sourceRequestNo(item))).length,
      totalAmount: items.reduce((sum, item) => sum + Number(item.lineAmount || 0), 0),
      waitingReceiptCount: items.filter((item) => item.orderStatus === "WAITING_RECEIPT").length,
      receivedCount: items.filter((item) => item.receiptStatus === "RECEIVED").length
    }))
    .sort((a, b) => a.name.localeCompare(b.name, "zh-CN"));
}

function buildSupplierArchives(rows: ProcurementOrderVO[]): SupplierArchive[] {
  const grouped = new Map<string, ProcurementOrderVO[]>();
  for (const order of rows) {
    const key = supplierArchiveKey(order);
    const items = grouped.get(key);
    if (items) {
      items.push(order);
    } else {
      grouped.set(key, [order]);
    }
  }
  return [...grouped.entries()]
    .map(([key, items]) => ({
      key,
      supplierId: items[0]?.supplierId,
      name: items[0]?.supplierName || "未命名供应商",
      items,
      totalAmount: items.reduce((sum, item) => sum + Number(item.lineAmount || 0), 0),
      waitingReceiptCount: items.filter((item) => item.orderStatus === "WAITING_RECEIPT").length,
      receivedCount: items.filter((item) => item.receiptStatus === "RECEIVED").length,
      invoiceUploadedCount: items.filter((item) => item.latestInvoice).length,
      invoiceAcceptedCount: items.filter((item) => item.latestInvoice?.status === "ACCEPTED").length,
      deliveryNoteUploadedCount: items.filter((item) => item.latestDeliveryNote).length,
      deliveryNoteAcceptedCount: items.filter((item) => item.latestDeliveryNote?.status === "ACCEPTED").length
    }))
    .sort((a, b) => b.totalAmount - a.totalAmount || a.name.localeCompare(b.name, "zh-CN"));
}

function archiveRoomDeptName(order: ProcurementOrderVO): string {
  return order.deptName?.trim() || order.deliveryAddress?.trim() || order.receivingCity?.trim() || "未归属机房";
}

function archiveRoomDisplayName(deptName: string): string {
  const normalized = deptName.replace(/^\[[^\]]+\]\s*/, "").trim();
  const parts = normalized.split(/\s*[-/＞>]\s*/).map((part) => part.trim()).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[parts.length - 2]} / ${parts[parts.length - 1]}`;
  }
  return normalized || "未归属机房";
}

function supplierArchiveKey(order: ProcurementOrderVO): string {
  return order.supplierId != null ? `supplier:${order.supplierId}` : `supplier-name:${order.supplierName || "unknown"}`;
}

function supplierAwardSummary(supplier: SupplierArchive): string {
  const names = unique(supplier.items.map((item) => item.itemName || "").filter(Boolean));
  if (names.length <= 2) {
    return names.join("、") || "中标商品";
  }
  return `${names.slice(0, 2).join("、")} 等 ${names.length} 项`;
}

function supplierArchiveCompletion(supplier: SupplierArchive): number {
  if (supplier.items.length === 0) {
    return 0;
  }
  const documentEligibleItems = supplier.items.filter((item) => canAccessInvoice(item));
  const requiredRecords = documentEligibleItems.length * 2;
  if (requiredRecords === 0) {
    return 0;
  }
  const completedRecords = documentEligibleItems.filter((item) => item.latestInvoice).length
    + documentEligibleItems.filter((item) => item.latestDeliveryNote).length;
  return Math.round(completedRecords / requiredRecords * 100);
}

function canExportSupplierArchive(supplier: SupplierArchive): boolean {
  return supplier.supplierId != null && supplier.items.some((order) => order.canExportOrder === true);
}

function documentStatusLabel(status: OrderInvoiceStatus, documentType: OrderDocumentType): string {
  const map: Record<OrderInvoiceStatus, string> = {
    PENDING: "待确认",
    ACCEPTED: documentType === "DELIVERY_NOTE" ? "已确认" : "已收票",
    RETURNED: "已退回，待重传",
    SUPERSEDED: "已被新版本替代"
  };
  return map[status];
}

function invoiceStatusLabel(status: OrderInvoiceStatus): string {
  return documentStatusLabel(status, "INVOICE");
}

function invoiceHistoryStatusLabel(status: OrderInvoiceStatus, index: number, documentType: OrderDocumentType): string {
  if (status === "RETURNED" && index > 0) {
    return "已退回（已重传）";
  }
  return documentStatusLabel(status, documentType);
}

function submittedRoleLabel(role?: string): string {
  const map: Record<string, string> = {
    SUPPLIER: "供应商",
    ASSISTANT: "部门助理",
    ADMIN: "管理员"
  };
  return map[role || ""] || "未知身份";
}

function invoiceStatusType(status: OrderInvoiceStatus): "success" | "warning" | "danger" | "info" {
  const map: Record<OrderInvoiceStatus, "success" | "warning" | "danger" | "info"> = {
    PENDING: "warning",
    ACCEPTED: "success",
    RETURNED: "danger",
    SUPERSEDED: "info"
  };
  return map[status];
}

function invoiceAlertType(status: OrderInvoiceStatus): "success" | "warning" | "info" | "error" {
  if (status === "ACCEPTED") {
    return "success";
  }
  if (status === "RETURNED") {
    return "error";
  }
  return status === "PENDING" ? "warning" : "info";
}

function invoiceLatestDescription(invoice: OrderInvoiceSummaryVO): string {
  const parts = [
    `${invoice.attachmentCount || 0} 个文件`,
    `提交时间：${formatDateTime(invoice.submittedAt)}`
  ];
  if (invoice.reviewRemark) {
    parts.push(`${invoice.status === "RETURNED" ? "退回原因" : "审核备注"}：${invoice.reviewRemark}`);
  }
  return parts.join("；");
}

function showReplacementFields(): boolean {
  return afterSaleForm.afterSaleType === "REPLACE_ITEM";
}

function receiptStatusLabel(order: ProcurementOrderVO): string {
  if (order.receiptStatus === "RECEIVED") {
    return order.autoReceived ? "已收货（系统自动）" : "已收货";
  }
  if (order.supplierConfirmStatus === "CONFIRMED") {
    return "待收货";
  }
  return "-";
}

function afterSaleSummary(order: ProcurementOrderVO): string {
  const parts = [`原因：${order.latestAfterSaleReason || "-"}`];
  if (order.replacementItemName) {
    parts.push(`更换商品：${order.replacementItemName} / ${order.replacementSpecModel || "-"}`);
  }
  if (order.latestApprovalRemark) {
    parts.push(`${order.latestAfterSaleStatus === "PROPOSAL_PENDING" ? "供应商方案" : "处理意见"}：${order.latestApprovalRemark}`);
  }
  return parts.join("；");
}

function formatQty(value?: number): string {
  if (value == null) {
    return "-";
  }
  return Number(value).toLocaleString("zh-CN", { maximumFractionDigits: 4 });
}

function formatMoney(value?: number): string {
  if (value == null) {
    return "-";
  }
  return `¥${Number(value).toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function formatOptionalMoney(value?: number): string {
  return value == null ? "-" : formatMoney(value);
}

function formatFileSize(value?: number): string {
  if (value == null || value < 0) {
    return "大小未知";
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function formatDateTime(value?: string): string {
  if (!value) {
    return "-";
  }
  return value.replace("T", " ").slice(0, 19);
}

function safeFileName(value: string): string {
  return value.replace(/[\\/:*?"<>|\s]+/g, "-");
}

onMounted(() => {
  loadAll();
});
</script>

<style scoped>
.order-cockpit-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-right: 36px;
}

.order-cockpit-title > div {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.order-cockpit-title span {
  color: #667794;
  font-size: 14px;
}

.order-cockpit-title strong {
  color: #10203d;
  font-size: 20px;
}

.order-cockpit {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.order-command-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid #dce5f2;
  border-radius: 6px;
  background: #f8fbff;
}

.order-command-bar dl {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 0;
}

.order-command-bar dl > div {
  min-width: 0;
  padding: 0 16px;
  border-right: 1px solid #dce5f2;
}

.order-command-bar dl > div:first-child {
  padding-left: 0;
}

.order-command-bar dl > div:last-child {
  border-right: 0;
}

.order-command-bar dt,
.source-order-grid span {
  color: #6f7f99;
  font-size: 12px;
}

.order-command-bar dd {
  overflow: hidden;
  margin: 5px 0 0;
  color: #17315d;
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-progress-track {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  margin: 0;
  padding: 20px 8px 18px;
  border: 1px solid #dfe7f2;
  border-radius: 6px;
  list-style: none;
}

.order-progress-track li {
  position: relative;
  display: grid;
  justify-items: center;
  gap: 6px;
  color: #8b98ad;
  text-align: center;
}

.order-progress-track li:not(:last-child)::after {
  position: absolute;
  top: 17px;
  left: calc(50% + 24px);
  width: calc(100% - 48px);
  height: 2px;
  background: #d9e0ea;
  content: "";
}

.order-progress-track li.is-done:not(:last-child)::after {
  background: #2f6bff;
}

.progress-dot {
  z-index: 1;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 2px solid #cfd7e3;
  border-radius: 50%;
  background: #fff;
  color: #8b98ad;
  font-size: 13px;
  font-weight: 700;
}

.order-progress-track li.is-done .progress-dot {
  border-color: #2f6bff;
  background: #2f6bff;
  color: #fff;
}

.order-progress-track li.is-current .progress-dot {
  border-color: #2f6bff;
  color: #2f6bff;
  box-shadow: 0 0 0 4px #eaf1ff;
}

.order-progress-track strong {
  color: #263a60;
  font-size: 13px;
}

.order-progress-track small {
  min-height: 17px;
  color: #7a8aa3;
  font-size: 12px;
}

.order-detail-tabs {
  display: flex;
  align-items: center;
  gap: 24px;
  overflow-x: auto;
  border-bottom: 1px solid #dce5f2;
}

.order-detail-tabs button {
  display: inline-flex;
  height: 40px;
  align-items: center;
  gap: 7px;
  padding: 0 3px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #526785;
  cursor: pointer;
}

.order-detail-tabs button.active {
  border-bottom-color: #165dff;
  color: #165dff;
  font-weight: 700;
}

.order-detail-tabs em {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 10px;
  background: #e9eff8;
  color: inherit;
  font-style: normal;
  font-size: 12px;
  text-align: center;
}

.order-tab-panel {
  min-height: 240px;
  min-width: 0;
}

.order-product-table {
  width: 100%;
  max-width: 100%;
}

.order-total-row {
  display: flex;
  justify-content: flex-end;
  gap: 36px;
  padding: 14px 18px;
  color: #526785;
}

.order-total-row strong {
  color: #165dff;
  font-size: 18px;
}

.order-insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 12px;
  margin-top: 2px;
}

.order-insight-grid > article,
.document-overview-grid > article {
  padding: 14px 16px;
  border: 1px solid #dfe7f2;
  border-radius: 6px;
  background: #fbfdff;
}

.order-insight-grid h4 {
  margin: 0 0 12px;
  color: #17315d;
}

.order-insight-grid > article > strong {
  color: #10203d;
  font-size: 15px;
}

.order-insight-grid p {
  margin: 7px 0 0;
  color: #667794;
  line-height: 1.6;
}

.order-event {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 9px;
  align-items: start;
  margin-top: 9px;
}

.order-event > span {
  width: 8px;
  height: 8px;
  margin-top: 5px;
  border-radius: 50%;
  background: #2f6bff;
}

.order-event div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.order-event strong {
  color: #263a60;
  font-size: 13px;
}

.order-event small {
  color: #7a8aa3;
  white-space: nowrap;
}

.document-overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.document-overview-grid > article {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 14px;
  align-items: start;
}

.document-overview-grid > article > div {
  display: grid;
  gap: 5px;
}

.document-overview-grid > article span {
  color: #7a8aa3;
  font-size: 12px;
}

.document-overview-grid .el-button {
  grid-column: 1 / -1;
  justify-self: start;
}

.source-order-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.source-order-grid > div {
  display: grid;
  gap: 5px;
  padding: 13px 14px;
  border: 1px solid #dfe7f2;
  border-radius: 6px;
  background: #fbfdff;
}

.source-order-grid strong {
  color: #263a60;
  overflow-wrap: anywhere;
}

.order-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.all-supplier-access-tag {
  margin-bottom: 12px;
}

.order-tab {
  height: 32px;
  padding: 0 14px;
  border: 1px solid #d7e1ef;
  border-radius: 4px;
  background: #f8fbff;
  color: #334e76;
  cursor: pointer;
}

.order-tab.active {
  border-color: #2563eb;
  background: #edf4ff;
  color: #0b56f0;
  font-weight: 600;
}

.order-search :deep(.el-input) {
  width: 390px;
}

.order-list {
  min-height: 180px;
}

.kind-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  padding: 4px;
  border: 1px solid #dfe7f2;
  border-radius: 6px;
  background: #f8fbff;
}

.kind-tab {
  display: inline-flex;
  height: 34px;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: #445b7c;
  cursor: pointer;
}

.kind-tab.active {
  border-color: #2563eb;
  background: #fff;
  color: #0b56f0;
  font-weight: 700;
  box-shadow: 0 1px 4px rgba(15, 38, 76, 0.08);
}

.kind-tab em {
  min-width: 22px;
  padding: 1px 6px;
  border-radius: 10px;
  background: #e9eff8;
  color: #405a80;
  font-style: normal;
  font-size: 12px;
  text-align: center;
}

.kind-tab.active em {
  background: #e7f0ff;
  color: #0b56f0;
}

.group-table,
.detail-table {
  border: 1px solid #e1e8f2;
  border-radius: 6px;
}

.order-no-link {
  height: auto;
  padding: 0;
  font-weight: 700;
}

.order-no-link :deep(span) {
  white-space: normal;
}

.muted-line {
  margin-top: 4px;
  color: #6f7f99;
  font-size: 12px;
}

.status-stack,
.product-cell {
  display: grid;
  gap: 4px;
  color: #5f7190;
  font-size: 13px;
}

.product-cell strong {
  color: #10203d;
}

.table-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.table-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.pager-box {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.detail-page {
  min-height: 260px;
}

.detail-toolbar {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 14px;
  align-items: center;
  margin-bottom: 16px;
}

.detail-title {
  display: grid;
  gap: 4px;
}

.detail-title strong {
  color: #10203d;
  font-size: 18px;
}

.detail-title span {
  color: #6b7d99;
  font-size: 13px;
}

.detail-summary {
  display: grid;
  grid-template-columns: repeat(6, minmax(120px, 1fr));
  margin: 0 0 16px;
  border: 1px solid #e1e8f2;
  border-radius: 6px;
  overflow: hidden;
}

.detail-summary div {
  padding: 12px 14px;
  border-right: 1px solid #e1e8f2;
  background: #f8fbff;
}

.detail-summary div:last-child {
  border-right: none;
}

.detail-summary dt {
  margin-bottom: 6px;
  color: #6b7d99;
  font-size: 12px;
}

.detail-summary dd {
  margin: 0;
  color: #10203d;
  font-size: 15px;
  font-weight: 700;
}

.detail-filters {
  margin-bottom: 12px;
}

.filter-select {
  width: 180px;
}

.detail-keyword {
  width: 260px;
}

.detail-alert {
  margin-top: 14px;
}

.archive-summary {
  grid-template-columns: repeat(6, minmax(130px, 1fr));
}

.archive-summary dd {
  overflow-wrap: anywhere;
}

.archive-section {
  margin-top: 16px;
}

.archive-section-heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.archive-section-heading strong {
  color: #10203d;
  font-size: 16px;
}

.archive-section-heading span {
  color: #6f7f99;
  font-size: 12px;
}

.archive-table :deep(.el-table__row) {
  height: 68px;
}

.archive-primary-cell {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.archive-primary-cell strong {
  color: #10203d;
  font-size: 15px;
  overflow-wrap: anywhere;
}

.archive-primary-cell span {
  color: #6f7f99;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.supplier-banner {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 180px 150px;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #dce5f2;
  border-radius: 6px;
  background: #f8fbff;
}

.supplier-banner-metric {
  display: grid;
  gap: 4px;
  padding-left: 16px;
  border-left: 1px solid #dce5f2;
}

.supplier-banner-metric span {
  color: #6f7f99;
  font-size: 12px;
}

.supplier-banner-metric strong {
  color: #10203d;
  font-size: 17px;
}

.archive-tabs {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-top: 14px;
  border-bottom: 1px solid #dce5f2;
}

.archive-tab {
  display: inline-flex;
  height: 42px;
  align-items: center;
  gap: 7px;
  padding: 0 4px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: #526785;
  cursor: pointer;
}

.archive-tab.active {
  border-bottom-color: #165dff;
  color: #165dff;
  font-weight: 700;
}

.archive-tab em {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 10px;
  background: #e9eff8;
  color: inherit;
  font-style: normal;
  font-size: 12px;
  text-align: center;
}

.archive-document-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid #dce5f2;
  border-radius: 6px;
  background: #f8fbff;
}

.archive-document-toolbar span {
  color: #667794;
  font-size: 12px;
  line-height: 1.5;
}

.batch-document-table {
  margin-top: 14px;
}

.batch-document-message {
  margin-top: 5px;
  color: #667794;
  font-size: 12px;
  line-height: 1.4;
}

.batch-document-summary {
  float: left;
  color: #526785;
  font-size: 13px;
  line-height: 32px;
}

.supplier-archive .archive-table {
  margin-top: 12px;
}

.inline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
}

.inline-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.invoice-dialog-body {
  min-height: 160px;
}

.document-type-switch {
  margin-bottom: 16px;
}

.document-status-list {
  display: grid;
  gap: 6px;
}

.document-status-row {
  display: grid;
  grid-template-columns: 48px minmax(72px, 1fr);
  align-items: center;
  gap: 8px;
  min-height: 24px;
}

.invoice-upload-section,
.invoice-history-section {
  margin-top: 18px;
}

.invoice-section-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 14px;
}

.invoice-section-title strong {
  color: #10203d;
  font-size: 16px;
}

.invoice-section-title span,
.invoice-upload-tip {
  color: #6f7f99;
  font-size: 12px;
}

.invoice-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 18px;
}

.invoice-field {
  width: 100%;
}

.invoice-upload-tip {
  margin-top: 5px;
  line-height: 1.5;
}

.invoice-upload-actions,
.invoice-review-actions {
  display: flex;
  justify-content: flex-end;
}

.invoice-history-card {
  margin-top: 12px;
  padding: 14px 16px;
  border: 1px solid #dfe7f2;
  border-radius: 6px;
  background: #fbfdff;
}

.invoice-history-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #6b7d99;
  font-size: 12px;
}

.invoice-history-card header div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.invoice-history-card header strong {
  color: #10203d;
  font-size: 15px;
}

.invoice-meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px 18px;
  margin: 14px 0 0;
}

.invoice-meta-grid div {
  min-width: 0;
}

.invoice-meta-grid dt {
  color: #6f7f99;
  font-size: 12px;
}

.invoice-meta-grid dd {
  margin: 3px 0 0;
  color: #243a5e;
  overflow-wrap: anywhere;
}

.invoice-note {
  margin: 12px 0 0;
  color: #526785;
}

.invoice-review-alert {
  margin-top: 12px;
}

.invoice-attachments {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 10px;
  margin-top: 12px;
  color: #526785;
}

.invoice-attachments :deep(.el-button + .el-button) {
  margin-left: 0;
}

.invoice-review-actions {
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #d9e3f0;
}

@media (max-width: 1180px) {
  .order-command-bar dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: 12px;
  }

  .order-command-bar dl > div:nth-child(2) {
    border-right: 0;
  }

  .detail-summary {
    grid-template-columns: repeat(3, minmax(120px, 1fr));
  }

  .detail-summary div {
    border-bottom: 1px solid #e1e8f2;
  }

  .invoice-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .supplier-banner {
    grid-template-columns: minmax(220px, 1fr) 160px 130px;
  }
}

@media (max-width: 900px) {
  .order-command-bar,
  .order-insight-grid,
  .document-overview-grid,
  .source-order-grid {
    grid-template-columns: 1fr;
  }

  .order-command-bar > .el-button {
    justify-self: start;
  }
}

@media (max-width: 700px) {

  .order-progress-track {
    overflow-x: auto;
    grid-template-columns: repeat(7, minmax(130px, 1fr));
  }

  .invoice-form-grid,
  .invoice-meta-grid {
    grid-template-columns: 1fr;
  }

  .supplier-banner {
    grid-template-columns: 1fr;
  }

  .supplier-banner-metric {
    padding: 10px 0 0;
    border-top: 1px solid #dce5f2;
    border-left: 0;
  }

  .archive-document-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
