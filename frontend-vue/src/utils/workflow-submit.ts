import { h } from "vue";
import { ElMessageBox } from "element-plus";
import type { WorkflowSubmitResultVO } from "../api/workflow";

export async function confirmSubmitNextApprovalDialog(result?: WorkflowSubmitResultVO | null): Promise<boolean> {
  try {
    await ElMessageBox.confirm(
      buildNextApprovalContent(result, "confirm"),
      {
        title: "",
        confirmButtonText: "确认提交",
        cancelButtonText: "取消",
        closeOnClickModal: false,
        closeOnPressEscape: true,
        distinguishCancelAndClose: true,
        autofocus: false,
        customClass: "next-approval-message-box next-approval-message-box--rich"
      }
    );
    return true;
  } catch {
    return false;
  }
}

export async function showSubmitNextApprovalDialog(result?: WorkflowSubmitResultVO | null): Promise<void> {
  const approvers = result?.approvers || [];
  if (!result?.hasNextApprover || approvers.length === 0) {
    return;
  }
  await ElMessageBox.alert(buildNextApprovalContent(result, "notice"), {
    title: "",
    confirmButtonText: "我知道了",
    closeOnClickModal: false,
    closeOnPressEscape: true,
    autofocus: false,
    customClass: "next-approval-message-box next-approval-message-box--rich"
  });
}

function buildNextApprovalContent(result: WorkflowSubmitResultVO | null | undefined, mode: "confirm" | "notice") {
  const approvers = result?.approvers || [];
  const approverText = approvers.map(formatApprover).filter(Boolean).join("、") || "-";
  const requestText = result?.reqNo || "-";
  const title = mode === "confirm" ? "提交前确认" : "流程已流转";
  const headline = mode === "confirm" ? "确认提交这张采购申请" : "采购申请已进入下一步";

  return h("section", { class: "next-approval-card" }, [
    h("header", { class: "next-approval-header" }, [
      h("div", { class: "next-approval-icon", "aria-hidden": "true" }, [
        h("span")
      ]),
      h("div", { class: "next-approval-title" }, [
        h("p", title),
        h("h3", headline)
      ])
    ]),
    h("div", { class: "next-approval-summary" }, [
      h("div", { class: "next-approval-field" }, [
        h("span", "申请单号"),
        h("strong", requestText)
      ]),
      h("div", { class: "next-approval-field next-approval-field--primary" }, [
        h("span", "下一步审核人"),
        h("strong", approverText)
      ])
    ])
  ]);
}

function formatApprover(item: { displayName?: string; realName?: string; username?: string; employeeNo?: string }): string {
  return item.realName || stripEmployeeNo(item.displayName) || item.username || item.employeeNo || "";
}

function stripEmployeeNo(value?: string): string {
  return (value || "").replace(/[\s\u3000]+\d{3,}$/u, "");
}
