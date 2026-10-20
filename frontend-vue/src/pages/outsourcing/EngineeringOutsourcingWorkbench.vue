<template>
  <div class="outsourcing-module">
    <main class="module-content">
        <template v-if="page === 'dashboard'">
          <ITOutsourcingDashboard />
        </template>

        <template v-else-if="page === 'supplier-dashboard'">
          <div class="page-heading">
            <div>
              <h3>协同工作台</h3>
              <p>查看服务商工单、入场计划、工作量与账单进度</p>
            </div>
            <el-button :icon="Download">导出待办</el-button>
          </div>

          <div class="metric-grid">
            <article v-for="metric in dashboardMetrics" :key="metric.label" class="metric-card">
              <span :class="['metric-icon', metric.tone]"><el-icon><component :is="metric.icon" /></el-icon></span>
              <div><span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><small>{{ metric.note }}</small></div>
            </article>
          </div>

          <div class="dashboard-grid dashboard-grid--single">
            <section class="module-panel">
              <header class="panel-heading"><div><h4>{{ portal === "internal" ? "本月结算" : "本月账单" }}</h4><span>按结算归属分账</span></div></header>
              <div class="settlement-summary">
                <div><span>云产线</span><strong>¥88,600</strong><el-progress :percentage="72" :show-text="false" /></div>
                <div><span>数据中心</span><strong>¥158,720</strong><el-progress :percentage="88" :show-text="false" color="#6b5bd2" /></div>
                <div><span>争议金额</span><strong class="danger-text">¥3,200</strong><el-progress :percentage="18" :show-text="false" color="#df5b5b" /></div>
              </div>
              <div class="panel-note">云产线订单独立结算，不与数据中心账单混合。</div>
            </section>
          </div>

          <section class="module-panel process-panel">
            <header class="panel-heading"><div><h4>标准履约流程</h4><span>工程外包工单独立流转，审批模板复用平台审批引擎</span></div></header>
            <div class="process-flow">
              <template v-for="(step, index) in workflowSteps" :key="step">
                <div><span :class="{ done: index < 3 }">{{ index + 1 }}</span><strong>{{ step }}</strong></div>
                <el-icon v-if="index < workflowSteps.length - 1"><ArrowRight /></el-icon>
              </template>
            </div>
          </section>

          <section class="module-panel">
            <header class="panel-heading"><div><h4>最近工单</h4><span>最新更新的工程外包任务</span></div><el-button text type="primary" @click="goPage(portal === 'internal' ? 'orders' : 'supplier-orders')">工单管理</el-button></header>
            <el-table :data="visibleDashboardOrders" size="small" @row-click="openOrder">
              <el-table-column prop="id" label="工单号" width="168" />
              <el-table-column prop="title" label="工单标题" min-width="220" show-overflow-tooltip />
              <el-table-column label="归属" width="110"><template #default="scope"><span :class="['owner-badge', scope.row.owner === '云产线' ? 'cloud' : 'dc']">{{ scope.row.owner }}</span></template></el-table-column>
              <el-table-column prop="room" label="机房" width="90" />
              <el-table-column prop="provider" label="服务商" width="120" />
              <el-table-column label="金额" width="120"><template #default="scope">{{ scope.row.amount ? money(scope.row.amount) : "待核价" }}</template></el-table-column>
              <el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="statusType(scope.row.status)" effect="light">{{ scope.row.status }}</el-tag></template></el-table-column>
              <el-table-column label="操作" width="85"><template #default="scope"><el-button link type="primary" @click.stop="openOrder(scope.row)">查看</el-button></template></el-table-column>
            </el-table>
          </section>
        </template>

        <template v-else-if="page === 'orders' || page === 'supplier-orders'">
          <div class="page-heading">
            <div><h3>{{ portal === "internal" ? "工单管理" : "我的工单" }}</h3><p>{{ portal === "internal" ? "按归属、机房和履约状态管理工程外包工单" : "仅展示分配给本服务商的工程外包任务" }}</p></div>
            <el-button v-if="portal === 'internal'" type="primary" :icon="Plus" @click="openCreateDialog">新建工单</el-button>
          </div>
          <section class="module-panel">
            <div class="filter-bar">
              <el-input v-model="orderKeyword" :prefix-icon="Search" clearable placeholder="工单号 / 标题 / 机房 / 服务商" />
              <el-select v-model="orderStatus" clearable placeholder="全部状态"><el-option v-for="status in orderStatuses" :key="status" :label="status" :value="status" /></el-select>
              <el-button @click="orderKeyword = ''; orderStatus = ''">重置</el-button>
            </div>
            <el-table :data="filteredOrders" @row-click="openOrder">
              <el-table-column prop="id" label="工单号" width="170" />
              <el-table-column prop="title" label="工单标题" min-width="230" show-overflow-tooltip />
              <el-table-column label="归属" width="110"><template #default="scope"><span :class="['owner-badge', scope.row.owner === '云产线' ? 'cloud' : 'dc']">{{ scope.row.owner }}</span></template></el-table-column>
              <el-table-column prop="region" label="区域" width="90" />
              <el-table-column prop="room" label="机房" width="90" />
              <el-table-column prop="provider" label="服务商" width="120" />
              <el-table-column label="金额" width="115"><template #default="scope">{{ scope.row.amount ? money(scope.row.amount) : "待核价" }}</template></el-table-column>
              <el-table-column prop="due" label="计划时间" width="125" />
              <el-table-column label="状态" width="105"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag></template></el-table-column>
              <el-table-column label="操作" fixed="right" width="90"><template #default="scope"><el-button link type="primary" @click.stop="openOrder(scope.row)">查看工单</el-button></template></el-table-column>
            </el-table>
          </section>
        </template>

        <template v-else-if="page === 'entry' || page === 'supplier-entry'">
          <div class="page-heading"><div><h3>{{ portal === "internal" ? "入场与人员" : "入场预约" }}</h3><p>入场预约、人员白名单与离场记录统一管理</p></div><el-button type="primary" :icon="Plus">{{ portal === "internal" ? "新增入场规则" : "发起入场预约" }}</el-button></div>
          <section class="module-panel"><el-table :data="entryRows"><el-table-column prop="id" label="预约单号" width="150" /><el-table-column prop="order" label="工单号" width="170" /><el-table-column prop="provider" label="服务商" width="120" /><el-table-column prop="room" label="机房" width="90" /><el-table-column prop="people" label="入场人员" min-width="180" /><el-table-column prop="time" label="计划入场" width="145" /><el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="90"><template #default><el-button link type="primary">查看</el-button></template></el-table-column></el-table></section>
        </template>

        <template v-else-if="page === 'acceptance' || page === 'supplier-acceptance' || page === 'supplier-workload'">
          <div class="page-heading"><div><h3>{{ page === "supplier-workload" ? "工作量填报" : "验收管理" }}</h3><p>按服务项核对实际工作量、整改记录和验收结论</p></div><el-button type="primary" :icon="Edit">{{ portal === "supplier" ? "提交工作量" : "批量验收" }}</el-button></div>
          <section class="module-panel"><el-table :data="acceptanceRows"><el-table-column prop="order" label="工单号" width="170" /><el-table-column prop="title" label="工单标题" min-width="220" /><el-table-column prop="provider" label="服务商" width="120" /><el-table-column prop="room" label="机房" width="90" /><el-table-column prop="workload" label="申报工作量" width="140" /><el-table-column prop="submitted" label="提交时间" width="130" /><el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="100"><template #default><el-button link type="primary">查看明细</el-button></template></el-table-column></el-table></section>
        </template>

        <template v-else-if="page === 'settlement' || page === 'supplier-bills'">
          <div class="page-heading"><div><h3>{{ portal === "internal" ? "结算与对账" : "账单对账" }}</h3><p>云产线与数据中心账单分开生成、核对和归档</p></div><el-button :icon="Download">导出账单</el-button></div>
          <div class="bill-warning"><el-icon><Warning /></el-icon><span>云产线订单使用独立结算归属，不能合并进数据中心月度账单。</span></div>
          <section class="module-panel"><el-table :data="supplierSettlements"><el-table-column prop="id" label="账单号" min-width="190" /><el-table-column label="结算归属" width="120"><template #default="scope"><span :class="['owner-badge', scope.row.owner === '云产线' ? 'cloud' : 'dc']">{{ scope.row.owner }}</span></template></el-table-column><el-table-column prop="provider" label="服务商" width="120" /><el-table-column prop="period" label="账期" width="100" /><el-table-column prop="orders" label="工单数" width="90" /><el-table-column label="账单金额" width="125"><template #default="scope"><strong>{{ money(scope.row.amount) }}</strong></template></el-table-column><el-table-column label="争议金额" width="120"><template #default="scope"><span :class="{ 'danger-text': scope.row.dispute }">{{ money(scope.row.dispute) }}</span></template></el-table-column><el-table-column label="状态" width="125"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="90"><template #default><el-button link type="primary">对账</el-button></template></el-table-column></el-table></section>
        </template>

        <template v-else-if="page === 'providers' || page === 'supplier-profile'">
          <div class="page-heading"><div><h3>{{ portal === "internal" ? "服务商管理" : "服务能力档案" }}</h3><p>同一供应商主体按业务分类授权，采购与外包数据互相隔离</p></div><el-button v-if="portal === 'internal'" type="primary" :icon="Plus">开通外包服务商</el-button></div>
          <section class="module-panel"><el-table :data="portal === 'supplier' ? providers.filter(item => item.name === '神州邦邦') : providers"><el-table-column prop="name" label="服务商" width="150"><template #default="scope"><strong>{{ scope.row.name }}</strong><small class="cell-subtitle">{{ scope.row.code }}</small></template></el-table-column><el-table-column label="业务分类" min-width="260"><template #default="scope"><el-tag v-for="category in scope.row.categories" :key="category" size="small" class="category-tag">{{ category }}</el-tag></template></el-table-column><el-table-column prop="scope" label="服务范围" width="150" /><el-table-column prop="people" label="白名单人员" width="110" /><el-table-column prop="performance" label="履约评分" width="105" /><el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="100"><template #default><el-button link type="primary">查看档案</el-button></template></el-table-column></el-table></section>
        </template>

        <template v-else-if="page === 'catalog'">
          <div class="page-heading"><div><h3>服务项与价格</h3><p>按服务商维护普通、紧急和批量工单的协议单价</p></div><div><el-button :icon="Download">导出价格</el-button><el-button type="primary" :icon="Plus">新增服务项</el-button></div></div>
          <section class="module-panel">
            <div class="filter-bar"><el-input v-model="catalogKeyword" :prefix-icon="Search" clearable placeholder="服务项 / 服务商 / 分类" /><el-select v-model="catalogProvider" clearable placeholder="全部服务商"><el-option label="亚康" value="亚康" /><el-option label="神州邦邦" value="神州邦邦" /></el-select></div>
            <el-table :data="filteredServicePrices" height="520"><el-table-column prop="code" label="编码" width="110" /><el-table-column prop="provider" label="服务商" width="120" /><el-table-column prop="name" label="服务项" min-width="260" /><el-table-column prop="category" label="分类" width="110" /><el-table-column prop="unit" label="单位" width="80" /><el-table-column label="普通单价" width="110"><template #default="scope">{{ unitMoney(scope.row.normal) }}</template></el-table-column><el-table-column label="紧急单价" width="110"><template #default="scope">{{ scope.row.emergency ? unitMoney(scope.row.emergency) : "—" }}</template></el-table-column><el-table-column label="批量单价" width="110"><template #default="scope">{{ scope.row.bulk ? unitMoney(scope.row.bulk) : "—" }}</template></el-table-column><el-table-column label="操作" width="85"><template #default><el-button link type="primary">编辑</el-button></template></el-table-column></el-table>
          </section>
        </template>

        <template v-else-if="page === 'config' || page === 'settings'">
          <div class="page-heading"><div><h3>{{ page === "config" ? "机房外包配置" : "外包设置" }}</h3><p>配置适用范围、前置审批组、服务商和审批模板</p></div><el-button type="primary" :icon="Setting" @click="approvalVisible = true">审批模板</el-button></div>
          <div class="config-metrics"><article><span>已启用机房</span><strong>4</strong><small>华北一 3 · 华北二 1</small></article><article><span>独立部门</span><strong>1</strong><small>云产线可跨机房下单</small></article><article><span>外包服务商</span><strong>3</strong><small>按业务分类授权</small></article></div>
          <section class="module-panel"><el-table :data="roomRows"><el-table-column prop="region" label="区域" width="100" /><el-table-column prop="room" label="机房/部门" width="120" /><el-table-column prop="dispatcher" label="前置审批组" width="125" /><el-table-column prop="manager" label="十夫长" width="110" /><el-table-column prop="centurion" label="百夫长" width="110" /><el-table-column prop="services" label="服务项" width="90" /><el-table-column prop="providers" label="可用服务商" width="110" /><el-table-column label="审批配置" width="120"><template #default="scope"><el-tag :type="statusType(scope.row.approval)">{{ scope.row.approval }}</el-tag></template></el-table-column><el-table-column label="操作"><template #default><el-button link type="primary">编辑配置</el-button></template></el-table-column></el-table></section>
        </template>

        <template v-else-if="page === 'analytics'">
          <div class="page-heading"><div><h3>履约分析</h3><p>查看工单效率、履约质量与服务商贡献</p></div><el-button :icon="Download">导出分析</el-button></div>
          <div class="metric-grid"><article v-for="metric in analyticsMetrics" :key="metric.label" class="metric-card"><span :class="['metric-icon', metric.tone]"><el-icon><component :is="metric.icon" /></el-icon></span><div><span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><small>{{ metric.note }}</small></div></article></div>
          <div class="dashboard-grid"><section class="module-panel chart-card"><header class="panel-heading"><div><h4>服务商履约表现</h4><span>准时率 / 一次验收通过率</span></div></header><div v-for="item in providerPerformance" :key="item.name" class="performance-row"><strong>{{ item.name }}</strong><el-progress :percentage="item.value" /><span>{{ item.value }}%</span></div></section><section class="module-panel chart-card"><header class="panel-heading"><div><h4>结算归属</h4><span>本月已确认金额</span></div></header><div class="amount-split"><div><span>数据中心</span><strong>¥158,720</strong></div><div><span>云产线</span><strong>¥88,600</strong></div></div></section></div>
        </template>

        <template v-else>
          <div class="page-heading"><div><h3>{{ currentPageTitle }}</h3><p>工程外包供应商协同页面</p></div><el-button type="primary">发起操作</el-button></div>
          <section class="module-panel empty-panel"><el-empty description="暂无待处理记录" /></section>
        </template>
    </main>

    <el-dialog v-model="createVisible" title="新建工程外包工单" width="760px" destroy-on-close>
      <div class="dialog-tip"><el-icon><InfoFilled /></el-icon><span>申请人只选择服务项和工作量，服务商由前置审批组审批时指定。</span></div>
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="下单类型" required><el-radio-group v-model="createForm.owner"><el-radio-button label="数据中心" value="数据中心" /><el-radio-button label="云产线" value="云产线" /></el-radio-group></el-form-item>
          <el-form-item label="目标机房" required><el-select v-model="createForm.room"><el-option label="M6" value="M6" /><el-option label="B28" value="B28" /><el-option label="M3" value="M3" /><el-option label="廊坊一" value="廊坊一" /></el-select></el-form-item>
          <el-form-item label="工单标题" required class="span-2"><el-input v-model="createForm.title" placeholder="例如：M6机房综合布线整改" /></el-form-item>
          <el-form-item label="计价类型" required><el-select v-model="createForm.pricingMode"><el-option label="普通工单" value="普通工单" /><el-option label="紧急工单" value="紧急工单" /><el-option label="批量工单" value="批量工单" /></el-select></el-form-item>
          <el-form-item label="期望实施日期" required><el-date-picker v-model="createForm.date" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        </div>
        <div class="service-builder">
          <header><div><h4>服务项与工作量</h4><span>按协议服务项选择，不允许手工填写单价</span></div><el-tag>{{ createForm.items.length }} 项</el-tag></header>
          <div v-for="(item, index) in createForm.items" :key="`${item.name}-${index}`" class="service-line"><div><strong>{{ item.name }}</strong><span>{{ item.category }}</span></div><el-input-number v-model="item.quantity" :min="1" /><span>{{ item.unit }}</span><el-button link type="danger" @click="createForm.items.splice(index, 1)">删除</el-button></div>
          <div class="add-service"><el-select v-model="newServiceName" filterable placeholder="选择服务项"><el-option v-for="item in requestCatalog" :key="item.name" :label="`${item.name} · ${item.unit}`" :value="item.name" /></el-select><el-input-number v-model="newServiceQuantity" :min="1" /><el-button :icon="Plus" @click="addCreateService">增加</el-button></div>
        </div>
        <el-form-item label="工作说明与验收要求"><el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="填写实施范围、交付物和验收标准" /></el-form-item>
      </el-form>
      <div class="dialog-workflow"><span>提交后流程</span><b>前置审批组</b><el-icon><ArrowRight /></el-icon><b>十夫长</b><el-icon><ArrowRight /></el-icon><b>百夫长</b><el-icon><ArrowRight /></el-icon><b>服务商接单</b></div>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button @click="saveDraft">保存草稿</el-button><el-button type="primary" @click="submitCreateOrder">提交审批</el-button></template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="760px" direction="rtl" class="order-drawer" destroy-on-close>
      <template #header><div><small>工程外包工单</small><h3>{{ selectedOrder?.id }}</h3></div></template>
      <template v-if="selectedOrder">
        <div class="drawer-title"><span :class="['owner-badge', selectedOrder.owner === '云产线' ? 'cloud' : 'dc']">{{ selectedOrder.owner }}</span><h3>{{ selectedOrder.title }}</h3><el-tag :type="statusType(selectedOrder.status)">{{ selectedOrder.status }}</el-tag></div>
        <div v-if="needsProviderAssignment" class="action-banner"><el-icon><Operation /></el-icon><div><strong>当前任务：指定服务商</strong><span>比较协议价格与服务覆盖，指定后进入十夫长审核。</span></div></div>
        <section v-if="selectedOrder.assignment" class="record-card"><header><span>服务商指定记录</span><el-tag type="success">已通过</el-tag></header><div><strong>{{ selectedOrder.provider }}</strong><b>{{ money(selectedOrder.amount) }}</b></div><p>{{ selectedOrder.assignment.reason }}</p><small>{{ selectedOrder.assignment.assignedBy }} · {{ selectedOrder.assignment.assignedAt }}</small></section>
        <section v-if="selectedOrder.adjustment" :class="['change-card', selectedOrder.adjustment.status === '待申请人确认' ? 'pending' : 'resolved']"><header><div><span>服务项变更申请</span><strong>{{ selectedOrder.adjustment.requestedBy }} · {{ selectedOrder.adjustment.requestedAt }}</strong></div><el-tag :type="statusType(selectedOrder.adjustment.status)">{{ selectedOrder.adjustment.status }}</el-tag></header><p>{{ selectedOrder.adjustment.reason }}</p><div v-for="change in selectedOrder.adjustment.changes" :key="change.name" class="change-line"><b :class="change.delta > 0 ? 'positive' : 'negative'">{{ change.delta > 0 ? "增加" : "减少" }}</b><strong>{{ change.name }}</strong><span>{{ change.before }} → {{ change.after }} {{ change.unit }}</span><em>{{ signedMoney(change.amountDelta) }}</em></div><footer><span>原金额 {{ money(selectedOrder.adjustment.originalAmount) }}</span><strong>调整后 {{ money(selectedOrder.adjustment.proposedAmount) }}</strong></footer></section>
        <section class="detail-card"><h4>工单信息</h4><dl><div><dt>下单归属</dt><dd>{{ selectedOrder.owner }}</dd></div><div><dt>目标机房</dt><dd>{{ selectedOrder.region }} / {{ selectedOrder.room }}</dd></div><div><dt>申请人</dt><dd>{{ selectedOrder.applicant }}</dd></div><div><dt>服务商</dt><dd>{{ selectedOrder.provider === "待指定" ? "待前置审批组指定" : selectedOrder.provider }}</dd></div><div><dt>现场联系人</dt><dd>{{ selectedOrder.contact }}</dd></div><div><dt>预计金额</dt><dd>{{ selectedOrder.amount ? money(selectedOrder.amount) : "待核价" }}</dd></div></dl>
          <div v-if="selectedOrder.items?.length" class="detail-items"><div class="detail-item heading"><span>服务项</span><span>工作量</span><span>单价</span><span>小计</span></div><div v-for="item in selectedOrder.items" :key="item.name" class="detail-item"><strong>{{ item.name }}</strong><span>{{ item.quantity }} {{ item.unit }}</span><span>{{ item.unitPrice ? unitMoney(item.unitPrice) : "待核价" }}</span><strong>{{ item.subtotal ? money(item.subtotal) : "—" }}</strong></div></div>
        </section>
        <section class="workflow-card"><h4>流程进度</h4><div class="workflow-track"><div v-for="(step, index) in workflowSteps" :key="step" :class="workflowStepClass(index, selectedOrder)"><span><el-icon v-if="isWorkflowDone(index, selectedOrder)"><Check /></el-icon><template v-else>{{ index + 1 }}</template></span><strong>{{ step }}</strong><small>{{ workflowStepText(index, selectedOrder) }}</small></div></div></section>
        <div class="drawer-note"><el-icon><InfoFilled /></el-icon><span>{{ needsProviderAssignment ? "服务商必须由前置审批组指定，申请人不能直接派单。" : "实施阶段服务项增减由供应商申请，申请人确认后才更新验收与结算清单。" }}</span></div>
      </template>
      <template #footer>
        <div class="drawer-footer">
          <template v-if="needsProviderAssignment"><el-button type="danger" plain @click="rejectSelectedOrder">驳回工单</el-button><el-button type="primary" @click="openAssignment">指定服务商并通过</el-button></template>
          <template v-else-if="canReviewAdjustment"><el-button type="danger" plain @click="reviewAdjustment(false)">拒绝调整</el-button><el-button type="primary" @click="reviewAdjustment(true)">确认调整</el-button></template>
          <template v-else><el-button @click="detailVisible = false">关闭</el-button><el-button v-if="canRequestAdjustment" type="primary" :disabled="selectedOrder?.adjustment?.status === '待申请人确认'" @click="openAdjustment">{{ selectedOrder?.adjustment?.status === "待申请人确认" ? "等待申请人确认" : "申请调整服务项" }}</el-button><el-button v-else type="primary">查看完整记录</el-button></template>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="assignmentVisible" title="前置审批组 · 指定服务商并通过" width="860px" destroy-on-close>
      <div v-if="selectedOrder" class="assignment-summary"><div><span>申请人</span><strong>{{ selectedOrder.applicant }}</strong></div><div><span>目标机房</span><strong>{{ selectedOrder.region }} / {{ selectedOrder.room }}</strong></div><div><span>计价类型</span><strong>{{ selectedOrder.pricingMode }}</strong></div><div><span>服务项</span><strong>{{ selectedOrder.items.length }} 项</strong></div></div>
      <div class="assignment-heading"><div><h4>选择服务商</h4><p>只显示具备工程外包分类、目标机房服务范围和完整协议价格的服务商。</p></div><el-tag>{{ assignmentQuotes.filter(item => item.complete).length }} 家可选</el-tag></div>
      <div class="provider-options"><button v-for="quote in assignmentQuotes" :key="quote.name" type="button" :disabled="!quote.complete" :class="{ selected: assignmentProvider === quote.name, incomplete: !quote.complete }" @click="assignmentProvider = quote.name"><span class="radio-dot"><el-icon v-if="assignmentProvider === quote.name"><CircleCheck /></el-icon></span><div><strong>{{ quote.name }}</strong><small>{{ quote.scope }} · 履约评分 {{ quote.performance }}</small></div><span>覆盖 <b>{{ quote.covered }}/{{ selectedOrder?.items.length }}</b></span><div><small>协议总价</small><strong>{{ quote.complete ? money(quote.total) : "无法完整报价" }}</strong></div></button></div>
      <div v-if="selectedQuote" class="quote-breakdown"><header><div><h4>{{ selectedQuote.name }} 报价明细</h4><span>指定后锁定本次单价快照</span></div><strong>{{ money(selectedQuote.total) }}</strong></header><div v-for="item in selectedQuote.items" :key="item.name" class="quote-line"><strong>{{ item.name }}</strong><span>{{ item.quantity }} {{ item.unit }}</span><span>{{ item.unitPrice ? unitMoney(item.unitPrice) : "未维护" }}</span><strong>{{ item.subtotal ? money(item.subtotal) : "—" }}</strong></div></div>
      <el-form-item label="指定理由" required><el-input v-model="assignmentReason" type="textarea" :rows="3" placeholder="说明服务覆盖、协议价格和履约能力依据" /></el-form-item>
      <template #footer><el-button @click="assignmentVisible = false">取消</el-button><el-button type="primary" @click="confirmAssignment">确认指定并通过</el-button></template>
    </el-dialog>

    <el-dialog v-model="adjustmentVisible" title="申请调整服务项" width="820px" destroy-on-close>
      <div class="dialog-tip warning"><el-icon><Warning /></el-icon><span>调整不会立即生效，提交后由申请人 {{ selectedOrder?.applicant }} 确认。</span></div>
      <div class="adjustment-list"><div class="adjustment-row heading"><span>服务项</span><span>原数量</span><span>调整后</span><span>变化</span><span>调整后金额</span></div><div v-for="item in adjustmentItems" :key="item.code" :class="['adjustment-row', { changed: item.quantity !== item.originalQuantity }]"><div><strong>{{ item.name }}</strong><small>{{ unitMoney(item.unitPrice) }} / {{ item.unit }}</small></div><span>{{ item.originalQuantity || "新增" }} {{ item.originalQuantity ? item.unit : "" }}</span><el-input-number v-model="item.quantity" :min="0" /><span :class="item.quantity - item.originalQuantity > 0 ? 'positive' : item.quantity - item.originalQuantity < 0 ? 'negative' : ''">{{ signedQuantity(item.quantity - item.originalQuantity, item.unit) }}</span><strong>{{ money(item.quantity * item.unitPrice) }}</strong></div></div>
      <div class="add-adjustment"><el-select v-model="adjustmentNewCode" filterable placeholder="选择要增加的服务项"><el-option v-for="row in adjustmentAddableRows" :key="row.code" :label="`${row.name} · ${unitMoney(row.normal)}/${row.unit}`" :value="row.code" /></el-select><el-button :icon="Plus" :disabled="!adjustmentNewCode" @click="addAdjustmentItem">增加服务项</el-button></div>
      <el-form-item label="调整原因" required><el-input v-model="adjustmentReason" type="textarea" :rows="3" placeholder="说明现场变化、增加或减少的原因" /></el-form-item>
      <div class="adjustment-total"><span>原金额 <b>{{ money(selectedOrder?.amount || 0) }}</b></span><span>调整后 <strong>{{ money(adjustmentAmount) }}</strong></span><span :class="adjustmentAmount - (selectedOrder?.amount || 0) >= 0 ? 'positive' : 'negative'">差额 {{ signedMoney(adjustmentAmount - (selectedOrder?.amount || 0)) }}</span></div>
      <template #footer><el-button @click="adjustmentVisible = false">取消</el-button><el-button type="primary" @click="submitAdjustment">提交申请人确认</el-button></template>
    </el-dialog>

    <el-drawer v-model="approvalVisible" size="520px" title="工程外包审批模板">
      <div class="template-card"><span>流程模板</span><strong>工程外包三级审批模板</strong><el-tag type="success">配置完整</el-tag></div>
      <div class="approval-rows"><div v-for="row in approvalRows" :key="row.level"><span>{{ row.level }}</span><strong>{{ row.approver }}</strong><small>{{ row.condition }}</small></div></div>
      <div class="drawer-note"><el-icon><InfoFilled /></el-icon><span>前置审批组可指定服务商；后续节点复用平台 V2-V8 审批模板，不借用采购审批事务。</span></div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  ArrowRight,
  Box,
  Check,
  CircleCheck,
  Connection,
  Document,
  Download,
  Edit,
  InfoFilled,
  Operation,
  Plus,
  Search,
  Setting,
  Tickets,
  Warning
} from "@element-plus/icons-vue";
import { useAuthStore } from "../../stores/auth";
import ITOutsourcingDashboard from "./ITOutsourcingDashboard.vue";

type Portal = "internal" | "supplier";
type ServiceItem = { code?: string; name: string; category: string; unit: string; quantity: number; unitPrice?: number; subtotal?: number };
type ChangeItem = { name: string; unit: string; before: number; after: number; delta: number; amountDelta: number };
type Adjustment = { status: string; requestedBy: string; requestedAt: string; reason: string; originalAmount: number; proposedAmount: number; proposedItems: ServiceItem[]; changes: ChangeItem[] };
type Order = { id: string; title: string; owner: string; region: string; room: string; service: string; provider: string; status: string; amount: number; due: string; contact: string; applicant: string; pricingMode: string; approvalStage?: string; items: ServiceItem[]; assignment?: { reason: string; assignedBy: string; assignedAt: string }; adjustment?: Adjustment };
type PriceRow = { code: string; provider: string; name: string; category: string; unit: string; normal: number; emergency?: number; bulk?: number };

const workflowSteps = ["创建工单", "前置审批组", "十夫长审核", "百夫长审核", "服务商接单", "入场与实施", "工作量验收", "结算对账"];
const orderStatuses = ["待审批", "待入场", "实施中", "待验收", "待结算", "已结算"];

const orders = reactive<Order[]>([
  { id: "OUT-20260825-001", title: "M6机房综合布线整改", owner: "数据中心", region: "华北一", room: "M6", service: "综合布线-网线等2项", provider: "待指定", status: "待审批", amount: 0, due: "08-28 18:00", contact: "赵新", applicant: "赵新", pricingMode: "普通工单", approvalStage: "前置审批组", items: [{ code: "REQ-001", name: "综合布线-网线", category: "综合布线", unit: "米", quantity: 600 }, { code: "REQ-002", name: "综合布线-LC光纤", category: "综合布线", unit: "米", quantity: 200 }] },
  { id: "OUT-20260824-018", title: "B28机房服务器搬迁支持", owner: "数据中心", region: "华北一", room: "B28", service: "设备搬迁技术支持", provider: "亚康", status: "待入场", amount: 8600, due: "08-27 09:00", contact: "张强", applicant: "张强", pricingMode: "普通工单", items: [{ code: "YK-014", name: "设备搬迁-跨机房普通设备搬迁", category: "设备搬迁", unit: "台", quantity: 10, unitPrice: 160, subtotal: 1600 }, { code: "YK-031", name: "人天服务-一线城市（北上广深）", category: "人天服务", unit: "人天", quantity: 7, unitPrice: 800, subtotal: 5600 }] },
  { id: "OUT-20260823-011", title: "云产线跨机房网络割接", owner: "云产线", region: "华北一", room: "M3", service: "综合布线-网线等4项", provider: "神州邦邦", status: "实施中", amount: 18600, due: "08-26 23:00", contact: "王磊", applicant: "董博", pricingMode: "普通工单", items: [{ code: "SZBB-016", name: "综合布线-网线", category: "综合布线", unit: "米", quantity: 400, unitPrice: 2.5, subtotal: 1000 }, { code: "SZBB-018", name: "综合布线-AOC光纤", category: "综合布线", unit: "米", quantity: 600, unitPrice: 3, subtotal: 1800 }, { code: "SZBB-025", name: "安装调试-设备迁移-4U及以上", category: "设备搬迁", unit: "台", quantity: 10, unitPrice: 220, subtotal: 2200 }, { code: "SZBB-027", name: "人天服务-一线城市（北上广深）", category: "人天服务", unit: "人天", quantity: 20, unitPrice: 680, subtotal: 13600 }] },
  { id: "OUT-20260821-007", title: "华北二机柜标签复核", owner: "数据中心", region: "华北二", room: "廊坊一", service: "机柜标签盘点", provider: "海创集团", status: "待验收", amount: 4200, due: "08-26 16:00", contact: "李明", applicant: "李明", pricingMode: "普通工单", items: [] },
  { id: "OUT-20260818-003", title: "云产线存储扩容现场实施", owner: "云产线", region: "华北一", room: "M6", service: "存储设备上架实施", provider: "亚康", status: "待结算", amount: 24800, due: "08-25 17:00", contact: "孙悦", applicant: "董博", pricingMode: "普通工单", items: [] },
  { id: "OUT-20260812-026", title: "M3机房光纤链路整改", owner: "数据中心", region: "华北一", room: "M3", service: "光纤链路整改", provider: "神州邦邦", status: "已结算", amount: 9800, due: "08-20 18:00", contact: "刘畅", applicant: "刘畅", pricingMode: "普通工单", items: [] }
]);

const entryRows = [
  { id: "ENTRY-0825-06", order: "OUT-20260824-018", provider: "亚康", room: "B28", people: "周航、马骏等 4 人", time: "08-27 09:00", status: "待机房确认" },
  { id: "ENTRY-0825-04", order: "OUT-20260823-011", provider: "神州邦邦", room: "M3", people: "陈立、郭凯等 3 人", time: "08-26 22:00", status: "已确认" },
  { id: "ENTRY-0824-12", order: "OUT-20260821-007", provider: "海创集团", room: "廊坊一", people: "冯瑞等 2 人", time: "08-25 10:00", status: "已离场" }
];

const acceptanceRows = [
  { order: "OUT-20260821-007", title: "华北二机柜标签复核", provider: "海创集团", room: "廊坊一", workload: "84 台机柜", submitted: "08-25 17:26", status: "待验收" },
  { order: "OUT-20260820-015", title: "M6机房PDU巡检协作", provider: "神州邦邦", room: "M6", workload: "32 台 PDU", submitted: "08-25 15:10", status: "整改后复验" },
  { order: "OUT-20260818-003", title: "云产线存储扩容现场实施", provider: "亚康", room: "M6", workload: "12 台设备", submitted: "08-24 18:40", status: "已通过" },
  { order: "OUT-20260817-009", title: "B28机房线缆梳理", provider: "神州邦邦", room: "B28", workload: "6 人天", submitted: "08-24 16:30", status: "部分通过" }
];

const settlements = [
  { id: "BILL-CLOUD-202608-006", owner: "云产线", provider: "亚康", period: "2026-08", orders: 3, amount: 51600, dispute: 0, status: "待服务商确认" },
  { id: "BILL-CLOUD-202608-004", owner: "云产线", provider: "神州邦邦", period: "2026-08", orders: 5, amount: 74280, dispute: 3200, status: "有异议" },
  { id: "BILL-DC-202608-021", owner: "数据中心", provider: "神州邦邦", period: "2026-08", orders: 12, amount: 92840, dispute: 0, status: "待企业复核" },
  { id: "BILL-DC-202608-018", owner: "数据中心", provider: "海创集团", period: "2026-08", orders: 4, amount: 28600, dispute: 0, status: "双方已确认" },
  { id: "BILL-DC-202607-016", owner: "数据中心", provider: "亚康", period: "2026-07", orders: 8, amount: 64800, dispute: 0, status: "已归档" }
];

const providers = [
  { name: "神州邦邦", code: "SZBB", categories: ["采购供应商", "工程外包服务商"], scope: "华北一、华北二", people: 28, performance: "92.6", status: "已准入" },
  { name: "亚康", code: "YK", categories: ["工程外包服务商"], scope: "华北一", people: 16, performance: "89.4", status: "已准入" },
  { name: "海创集团", code: "HCJT", categories: ["采购供应商", "工程外包服务商", "维保服务商"], scope: "华北二", people: 11, performance: "86.8", status: "已准入" },
  { name: "鑫方盛", code: "XFS", categories: ["采购供应商"], scope: "—", people: 0, performance: "—", status: "未开通外包" }
];

const servicePrices: PriceRow[] = [
  { code: "YK-001", provider: "亚康", name: "综合布线-网线", category: "综合布线", unit: "米", normal: 2.9 },
  { code: "YK-002", provider: "亚康", name: "综合布线-LC光纤", category: "综合布线", unit: "米", normal: 3 },
  { code: "YK-003", provider: "亚康", name: "综合布线-AOC光纤", category: "综合布线", unit: "米", normal: 3.2 },
  { code: "YK-004", provider: "亚康", name: "综合布线-MPO光纤", category: "综合布线", unit: "米", normal: 3.2 },
  { code: "YK-005", provider: "亚康", name: "安装调试-普通设备上架", category: "安装调试", unit: "台", normal: 56 },
  { code: "YK-014", provider: "亚康", name: "设备搬迁-跨机房普通设备搬迁", category: "设备搬迁", unit: "台", normal: 160 },
  { code: "YK-031", provider: "亚康", name: "人天服务-一线城市（北上广深）", category: "人天服务", unit: "人天", normal: 800 },
  { code: "YK-032", provider: "亚康", name: "人天服务-二线城市（江苏、浙江）", category: "人天服务", unit: "人天", normal: 750 },
  { code: "SZBB-016", provider: "神州邦邦", name: "综合布线-网线", category: "综合布线", unit: "米", normal: 2.5, emergency: 3, bulk: 2 },
  { code: "SZBB-017", provider: "神州邦邦", name: "综合布线-LC光纤", category: "综合布线", unit: "米", normal: 2.9, emergency: 3.5, bulk: 2.3 },
  { code: "SZBB-018", provider: "神州邦邦", name: "综合布线-AOC光纤", category: "综合布线", unit: "米", normal: 3, emergency: 3.6, bulk: 2.4 },
  { code: "SZBB-019", provider: "神州邦邦", name: "综合布线-MPO光纤", category: "综合布线", unit: "米", normal: 3, emergency: 3.6 },
  { code: "SZBB-020", provider: "神州邦邦", name: "安装调试-设备上架-3U及以下", category: "安装调试", unit: "台", normal: 48, emergency: 57.6, bulk: 43.2 },
  { code: "SZBB-021", provider: "神州邦邦", name: "安装调试-设备上架-4U及以上", category: "安装调试", unit: "台", normal: 170, emergency: 205, bulk: 153 },
  { code: "SZBB-022", provider: "神州邦邦", name: "安装调试-设备下架-3U及以下", category: "安装调试", unit: "台", normal: 25, emergency: 30, bulk: 22.5 },
  { code: "SZBB-023", provider: "神州邦邦", name: "安装调试-设备下架-4U及以上", category: "安装调试", unit: "台", normal: 145, emergency: 175, bulk: 130.5 },
  { code: "SZBB-025", provider: "神州邦邦", name: "安装调试-设备迁移-4U及以上", category: "设备搬迁", unit: "台", normal: 220, emergency: 265, bulk: 198 },
  { code: "SZBB-027", provider: "神州邦邦", name: "人天服务-一线城市（北上广深）", category: "人天服务", unit: "人天", normal: 680, emergency: 952 },
  { code: "SZBB-028", provider: "神州邦邦", name: "人天服务-二线城市（江苏、浙江）", category: "人天服务", unit: "人天", normal: 650, emergency: 910 },
  { code: "SZBB-037", provider: "神州邦邦", name: "机柜裁撤-整柜拆除", category: "机柜裁撤", unit: "柜", normal: 180, emergency: 216 }
];

const roomRows = [
  { region: "华北一", room: "M6", dispatcher: "赵新", manager: "张强", centurion: "王琪", services: 12, providers: 3, approval: "配置完整" },
  { region: "华北一", room: "B28", dispatcher: "赵新", manager: "李敏", centurion: "王琪", services: 9, providers: 2, approval: "配置完整" },
  { region: "华北一", room: "M3", dispatcher: "刘畅", manager: "高峰", centurion: "王琪", services: 10, providers: 3, approval: "配置完整" },
  { region: "华北二", room: "廊坊一", dispatcher: "冯瑞", manager: "陈志成", centurion: "孙岩", services: 7, providers: 2, approval: "待补充" },
  { region: "独立部门", room: "云产线", dispatcher: "董博", manager: "王建", centurion: "—", services: 14, providers: 3, approval: "配置完整" }
];

const approvalRows = [{ level: "前置审批组", approver: "IT中心审批组", condition: "始终进入" }, { level: "V2", approver: "十夫长", condition: "始终进入" }, { level: "V3", approver: "百夫长", condition: "始终进入" }, { level: "V4-V8", approver: "按需要启用", condition: "模板配置" }];
const providerPerformance = [{ name: "神州邦邦", value: 93 }, { name: "亚康", value: 89 }, { name: "海创集团", value: 87 }];

const authStore = useAuthStore();
const route = useRoute();
const router = useRouter();
const supplierAccount = computed(() => authStore.user?.userType?.trim().toUpperCase() === "SUPPLIER" || authStore.effectiveRoleCodes.includes("SUPPLIER") || authStore.user?.supplierId != null);
const portal = computed<Portal>(() => supplierAccount.value ? "supplier" : "internal");
const page = computed(() => {
  const requestedPage = String(route.meta.outsourcingPage || "dashboard");
  const supplierAliases: Record<string, string> = {
    dashboard: "supplier-dashboard",
    orders: "supplier-orders",
    entry: "supplier-entry",
    settlement: "supplier-bills"
  };
  return supplierAccount.value ? (supplierAliases[requestedPage] || requestedPage) : requestedPage;
});
const orderKeyword = ref("");
const orderStatus = ref("");
const catalogKeyword = ref("");
const catalogProvider = ref("");
const createVisible = ref(false);
const detailVisible = ref(false);
const assignmentVisible = ref(false);
const adjustmentVisible = ref(false);
const approvalVisible = ref(false);
const selectedOrder = ref<Order | null>(null);
const newServiceName = ref("");
const newServiceQuantity = ref(1);
const assignmentProvider = ref("");
const assignmentReason = ref("服务项覆盖完整，协议价格清晰，且具备目标机房服务能力。");
const adjustmentReason = ref("");
const adjustmentNewCode = ref("");
const adjustmentItems = ref<Array<ServiceItem & { originalQuantity: number; unitPrice: number }>>([]);

const createForm = reactive({ owner: "数据中心", room: "M6", title: "", pricingMode: "普通工单", date: "2026-08-28", description: "", items: [] as ServiceItem[] });

const requestCatalog = computed(() => {
  const map = new Map<string, { name: string; category: string; unit: string }>();
  servicePrices.forEach(row => { if (!map.has(row.name)) map.set(row.name, { name: row.name, category: row.category, unit: row.unit }); });
  return [...map.values()];
});
const pagePaths: Record<string, string> = {
  dashboard: "/engineering-outsourcing/workbench",
  orders: "/engineering-outsourcing/orders",
  entry: "/engineering-outsourcing/entry",
  acceptance: "/engineering-outsourcing/acceptance",
  settlement: "/engineering-outsourcing/settlement",
  providers: "/engineering-outsourcing/providers",
  catalog: "/engineering-outsourcing/catalog",
  config: "/engineering-outsourcing/config",
  analytics: "/engineering-outsourcing/analytics",
  settings: "/engineering-outsourcing/settings",
  "supplier-dashboard": "/engineering-outsourcing/workbench",
  "supplier-orders": "/engineering-outsourcing/orders",
  "supplier-entry": "/engineering-outsourcing/entry",
  "supplier-workload": "/engineering-outsourcing/workload",
  "supplier-acceptance": "/engineering-outsourcing/acceptance-results",
  "supplier-bills": "/engineering-outsourcing/settlement",
  "supplier-profile": "/engineering-outsourcing/profile"
};
const pageTitles: Record<string, string> = {
  dashboard: "IT工程外包看板",
  orders: "工单管理",
  entry: "入场与人员",
  acceptance: "验收管理",
  settlement: "结算与对账",
  providers: "服务商管理",
  catalog: "服务项与价格",
  config: "机房外包配置",
  analytics: "履约分析",
  settings: "外包设置",
  "supplier-dashboard": "协同工作台",
  "supplier-orders": "我的工单",
  "supplier-entry": "入场预约",
  "supplier-workload": "工作量填报",
  "supplier-acceptance": "验收结果",
  "supplier-bills": "账单对账",
  "supplier-profile": "服务能力档案"
};
const currentPageTitle = computed(() => pageTitles[page.value] || "工程外包");
const visibleDashboardOrders = computed(() => portal.value === "supplier" ? orders.filter(order => order.provider === "神州邦邦").slice(0, 5) : orders.slice(0, 5));
const filteredOrders = computed(() => (portal.value === "supplier" ? orders.filter(order => order.provider === "神州邦邦") : orders).filter(order => (!orderStatus.value || order.status === orderStatus.value) && (!orderKeyword.value || [order.id, order.title, order.room, order.provider].join(" ").toLowerCase().includes(orderKeyword.value.toLowerCase()))));
const supplierSettlements = computed(() => portal.value === "supplier" ? settlements.filter(item => item.provider === "神州邦邦") : settlements);
const filteredServicePrices = computed(() => servicePrices.filter(row => (!catalogProvider.value || row.provider === catalogProvider.value) && (!catalogKeyword.value || [row.name, row.provider, row.category].join(" ").toLowerCase().includes(catalogKeyword.value.toLowerCase()))));
const dashboardMetrics = computed(() => portal.value === "internal" ? [
  { label: "本月工单", value: "32", note: "较上月 +18.5%", icon: Document, tone: "blue" },
  { label: "待处理事项", value: "14", note: "4 项今日到期", icon: Warning, tone: "orange" },
  { label: "一次验收通过率", value: "91.8%", note: "较上月 +2.3%", icon: CircleCheck, tone: "green" },
  { label: "本月待结算", value: "¥247,320", note: "云产线 ¥88,600", icon: Tickets, tone: "purple" }
] : [
  { label: "待接单", value: "2", note: "1 单今日到期", icon: Document, tone: "blue" },
  { label: "待入场", value: "1", note: "明日 09:00", icon: Connection, tone: "orange" },
  { label: "待填报工作量", value: "2", note: "实施中工单", icon: Edit, tone: "green" },
  { label: "待确认账单", value: "1", note: "争议 ¥3,200", icon: Tickets, tone: "purple" }
]);
const analyticsMetrics = [{ label: "平均派单时长", value: "2.4h", note: "较上月缩短 0.8h", icon: Operation, tone: "blue" }, { label: "按时到场率", value: "96.2%", note: "目标 ≥95%", icon: Connection, tone: "green" }, { label: "月度工作量", value: "1,842", note: "服务项计量单位汇总", icon: Box, tone: "orange" }, { label: "本月结算", value: "¥247,320", note: "不含争议金额", icon: Tickets, tone: "purple" }];

const needsProviderAssignment = computed(() => portal.value === "internal" && selectedOrder.value?.status === "待审批" && selectedOrder.value.approvalStage === "前置审批组" && selectedOrder.value.provider === "待指定");
const canRequestAdjustment = computed(() => portal.value === "supplier" && !!selectedOrder.value && ["待入场", "实施中"].includes(selectedOrder.value.status) && selectedOrder.value.items.length > 0);
const canReviewAdjustment = computed(() => portal.value === "internal" && selectedOrder.value?.adjustment?.status === "待申请人确认" && selectedOrder.value.applicant === "董博");
const assignmentQuotes = computed(() => {
  if (!selectedOrder.value) return [];
  const priceKey = selectedOrder.value.pricingMode === "紧急工单" ? "emergency" : selectedOrder.value.pricingMode === "批量工单" ? "bulk" : "normal";
  return providers.filter(provider => provider.categories.includes("工程外包服务商")).map(provider => {
    const items = selectedOrder.value!.items.map(item => {
      const price = servicePrices.find(row => row.provider === provider.name && row.name === item.name);
      const unitPrice = price ? Number(price[priceKey as keyof PriceRow] || 0) : 0;
      return { ...item, code: price?.code, unitPrice, subtotal: unitPrice ? unitPrice * item.quantity : 0 };
    });
    const covered = items.filter(item => item.unitPrice).length;
    return { ...provider, items, covered, complete: covered === selectedOrder.value!.items.length, total: items.reduce((sum, item) => sum + Number(item.subtotal || 0), 0) };
  });
});
const selectedQuote = computed(() => assignmentQuotes.value.find(item => item.name === assignmentProvider.value));
const adjustmentAddableRows = computed(() => servicePrices.filter(row => row.provider === selectedOrder.value?.provider && !adjustmentItems.value.some(item => item.code === row.code)));
const adjustmentAmount = computed(() => adjustmentItems.value.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0));

function money(value: number): string { return new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY", maximumFractionDigits: 0 }).format(value); }
function unitMoney(value: number): string { return new Intl.NumberFormat("zh-CN", { style: "currency", currency: "CNY", minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value); }
function signedMoney(value: number): string { return `${value >= 0 ? "+" : "-"}${money(Math.abs(value))}`; }
function signedQuantity(value: number, unit: string): string { return value === 0 ? "—" : `${value > 0 ? "+" : ""}${value} ${unit}`; }
function statusType(status: string): "success" | "warning" | "danger" | "info" | "primary" { if (["已通过", "已确认", "已离场", "已结算", "已归档", "双方已确认", "配置完整", "已准入"].includes(status)) return "success"; if (["有异议", "整改后复验", "部分通过", "待补充", "已拒绝", "未开通外包"].includes(status)) return "danger"; if (status.startsWith("待") || status === "实施中") return "warning"; return "info"; }
function goPage(targetPage: string): void { const target = pagePaths[targetPage]; if (target) void router.push(target); }
function openOrder(order: Order): void { selectedOrder.value = order; detailVisible.value = true; }
function openCreateDialog(): void { createVisible.value = true; }
function addCreateService(): void { const option = requestCatalog.value.find(item => item.name === newServiceName.value); if (!option) return; const existing = createForm.items.find(item => item.name === option.name); if (existing) existing.quantity += newServiceQuantity.value; else createForm.items.push({ ...option, quantity: newServiceQuantity.value }); newServiceName.value = ""; newServiceQuantity.value = 1; }
function saveDraft(): void { ElMessage.success("草稿已保存在当前测试会话"); }
function submitCreateOrder(): void { if (!createForm.title.trim() || !createForm.items.length) { ElMessage.warning("请填写工单标题并至少增加一个服务项"); return; } const id = `OUT-20260826-${String(orders.length + 1).padStart(3, "0")}`; orders.unshift({ id, title: createForm.title.trim(), owner: createForm.owner, region: createForm.room === "廊坊一" ? "华北二" : "华北一", room: createForm.room, service: createForm.items.length === 1 ? createForm.items[0].name : `${createForm.items[0].name}等${createForm.items.length}项`, provider: "待指定", status: "待审批", amount: 0, due: `${createForm.date.slice(5)} 18:00`, contact: "董博", applicant: "董博", pricingMode: createForm.pricingMode, approvalStage: "前置审批组", items: createForm.items.map(item => ({ ...item })) }); createVisible.value = false; createForm.title = ""; createForm.items.splice(0); ElMessage.success(`工单 ${id} 已进入前置审批组`); }
function openAssignment(): void { assignmentProvider.value = assignmentQuotes.value.find(item => item.complete)?.name || ""; assignmentVisible.value = true; }
function confirmAssignment(): void { if (!selectedOrder.value || !selectedQuote.value?.complete || !assignmentReason.value.trim()) { ElMessage.warning("请选择可完整报价的服务商并填写指定理由"); return; } selectedOrder.value.provider = selectedQuote.value.name; selectedOrder.value.amount = selectedQuote.value.total; selectedOrder.value.items = selectedQuote.value.items.map(item => ({ ...item })); selectedOrder.value.approvalStage = "十夫长审核"; selectedOrder.value.assignment = { reason: assignmentReason.value.trim(), assignedBy: "IT中心审批组 · 赵新", assignedAt: "2026-08-26 14:36" }; assignmentVisible.value = false; ElMessage.success("服务商已指定，工单进入十夫长审核"); }
async function rejectSelectedOrder(): Promise<void> { if (!selectedOrder.value) return; await ElMessageBox.confirm("驳回后申请人可修改并重新提交，是否继续？", "驳回工单", { type: "warning" }); selectedOrder.value.status = "已驳回"; selectedOrder.value.approvalStage = "已结束"; detailVisible.value = false; ElMessage.success("工单已驳回"); }
function openAdjustment(): void { if (!selectedOrder.value) return; adjustmentItems.value = selectedOrder.value.items.map(item => ({ ...item, code: item.code || item.name, originalQuantity: item.quantity, unitPrice: Number(item.unitPrice || 0) })); adjustmentReason.value = ""; adjustmentNewCode.value = ""; adjustmentVisible.value = true; }
function addAdjustmentItem(): void { const row = servicePrices.find(item => item.code === adjustmentNewCode.value); if (!row) return; adjustmentItems.value.push({ ...row, quantity: 1, originalQuantity: 0, unitPrice: row.normal }); adjustmentNewCode.value = ""; }
function submitAdjustment(): void { if (!selectedOrder.value) return; const changes = adjustmentItems.value.map(item => ({ name: item.name, unit: item.unit, before: item.originalQuantity, after: item.quantity, delta: item.quantity - item.originalQuantity, amountDelta: (item.quantity - item.originalQuantity) * item.unitPrice })).filter(item => item.delta !== 0); if (!changes.length || !adjustmentReason.value.trim()) { ElMessage.warning("请至少调整一个服务项并填写调整原因"); return; } selectedOrder.value.adjustment = { status: "待申请人确认", requestedBy: "神州邦邦管理员", requestedAt: "08-26 10:24", reason: adjustmentReason.value.trim(), originalAmount: selectedOrder.value.amount, proposedAmount: adjustmentAmount.value, proposedItems: adjustmentItems.value.filter(item => item.quantity > 0).map(item => ({ ...item, subtotal: item.quantity * item.unitPrice })), changes }; adjustmentVisible.value = false; ElMessage.success(`服务项调整已提交，等待申请人 ${selectedOrder.value.applicant} 确认`); }
function reviewAdjustment(approved: boolean): void { if (!selectedOrder.value?.adjustment) return; selectedOrder.value.adjustment.status = approved ? "已确认" : "已拒绝"; if (approved) { selectedOrder.value.items = selectedOrder.value.adjustment.proposedItems.map(item => ({ ...item })); selectedOrder.value.amount = selectedOrder.value.adjustment.proposedAmount; } ElMessage.success(approved ? "服务项调整已确认" : "服务项调整已拒绝，继续使用原清单"); }
function currentWorkflowStep(order: Order): number { if (order.approvalStage === "十夫长审核") return 2; return ({ "待审批": 1, "待入场": 5, "实施中": 5, "待验收": 6, "待结算": 7, "已结算": 8 } as Record<string, number>)[order.status] || 1; }
function isWorkflowDone(index: number, order: Order): boolean { return index < currentWorkflowStep(order) || currentWorkflowStep(order) === workflowSteps.length; }
function workflowStepClass(index: number, order: Order): Record<string, boolean> { return { done: isWorkflowDone(index, order), current: index === currentWorkflowStep(order) && currentWorkflowStep(order) < workflowSteps.length }; }
function workflowStepText(index: number, order: Order): string { if (isWorkflowDone(index, order)) return "已完成"; return index === currentWorkflowStep(order) ? "当前步骤" : "待处理"; }
</script>

<style scoped src="./EngineeringOutsourcingWorkbench.css"></style>
