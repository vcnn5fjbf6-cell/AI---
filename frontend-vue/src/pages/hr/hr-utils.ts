export const POSITION_GROUPS: Record<string, { group: string; icon: string; order: number }> = {
  区域总监: { group: "区域总监", icon: "👑", order: -3 },
  区域经理: { group: "区域经理", icon: "⭐", order: -2 },
  交付运维主管: { group: "主管", icon: "⭐", order: 0 },
  设施运维主管: { group: "主管", icon: "⭐", order: 0 },
  设施运维值班长: { group: "值班长", icon: "⭐", order: 1 },
  高级交付运维工程师: { group: "高级工程师", icon: "🔬", order: 2 },
  交付运维工程师: { group: "工程师", icon: "🔧", order: 3 },
  设施运维工程师: { group: "工程师", icon: "🔧", order: 3 },
  交付运维值班工程师: { group: "值班工程师", icon: "🛠️", order: 4 },
  设施运维值班工程师: { group: "值班工程师", icon: "🛠️", order: 4 },
  "交付运维工程师-网银专职": { group: "网银专职", icon: "💳", order: 5 },
  前台: { group: "前台", icon: "💁", order: 6 }
};

export function positionGroup(position?: string): { group: string; icon: string; order: number } {
  return POSITION_GROUPS[position || ""] || { group: "其他", icon: "👤", order: 99 };
}

export function isManager(position?: string): boolean {
  if (!position) return false;
  return ["主管", "值班长", "经理", "总监"].some((keyword) => position.includes(keyword));
}

export function managerTag(position?: string): { label: string; color: string } | null {
  if (!position) return null;
  if (position.includes("主管")) return { label: "主管", color: "#8b5cf6" };
  if (position.includes("值班长")) return { label: "值班长", color: "#f59e0b" };
  if (position.includes("经理")) return { label: "经理", color: "#ec4899" };
  if (position.includes("总监")) return { label: "总监", color: "#ef4444" };
  return null;
}

export function formatMoney(value?: number | null): string {
  const amount = value || 0;
  if (amount >= 10000) return `${(amount / 10000).toFixed(2)}万`;
  return `¥${amount.toFixed(0)}`;
}

export function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (ch === '"') {
      quoted = !quoted;
    } else if (ch === "," && !quoted) {
      row.push(cell);
      cell = "";
    } else if ((ch === "\n" || ch === "\r") && !quoted) {
      row.push(cell);
      if (row.some((item) => item.trim() !== "")) rows.push(row);
      row = [];
      cell = "";
    } else {
      cell += ch;
    }
  }
  row.push(cell);
  if (row.some((item) => item.trim() !== "")) rows.push(row);
  return rows;
}

export function downloadText(filename: string, text: string, mime = "text/plain;charset=utf-8"): void {
  const blob = new Blob([text], { type: mime });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}

export function readFileAsText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = async () => {
      const buffer = reader.result as ArrayBuffer;
      try {
        const utf8 = new TextDecoder("utf-8").decode(buffer).replace(/^\uFEFF/, "");
        if (utf8.includes("\uFFFD")) {
          resolve(new TextDecoder("gbk").decode(buffer).replace(/^\uFEFF/, ""));
          return;
        }
        resolve(utf8);
      } catch (error) {
        reject(error);
      }
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsArrayBuffer(file);
  });
}

export async function parseExcelOrCsvFile(file: File): Promise<string[][]> {
  const name = file.name.toLowerCase();
  if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
    const XLSX: any = (await import("../../vendor/xlsx")).default;
    const buffer = await file.arrayBuffer();
    const workbook = XLSX.read(buffer, { type: "array" });
    const sheet = workbook.Sheets[workbook.SheetNames[0]];
    const rows = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: "" }) as unknown[][];
    return rows
      .filter((row) => row.some((cell) => String(cell ?? "").trim() !== ""))
      .map((row) => row.map((cell) => String(cell ?? "").trim()));
  }
  return parseCsv(await readFileAsText(file));
}

export function avatarColor(name?: string): string {
  const palette = ["#6366f1", "#3b82f6", "#10b981", "#f59e0b", "#ef4444", "#14b8a6", "#ec4899", "#8b5cf6", "#06b6d4", "#d97706"];
  if (!name) return palette[0];
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = ((hash << 5) - hash + name.charCodeAt(i)) | 0;
  }
  return palette[Math.abs(hash) % palette.length];
}
