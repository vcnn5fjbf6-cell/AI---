export interface MeasurementUnitOption {
  label: string;
  value: string;
}

export interface MeasurementUnitGroup {
  label: string;
  options: MeasurementUnitOption[];
}

// Procurement-facing subset based on common trade units; keep storage as Chinese unit names.
export const PROCUREMENT_UNIT_GROUPS: MeasurementUnitGroup[] = [
  {
    label: "常用计数",
    options: [
      { label: "件", value: "件" },
      { label: "个", value: "个" },
      { label: "台", value: "台" },
      { label: "套", value: "套" },
      { label: "批", value: "批" },
      { label: "项", value: "项" },
      { label: "次", value: "次" },
      { label: "组", value: "组" },
      { label: "只", value: "只" },
      { label: "块", value: "块" }
    ]
  },
  {
    label: "包装",
    options: [
      { label: "箱", value: "箱" },
      { label: "包", value: "包" },
      { label: "盒", value: "盒" },
      { label: "袋", value: "袋" },
      { label: "瓶", value: "瓶" },
      { label: "桶", value: "桶" },
      { label: "罐", value: "罐" },
      { label: "卷", value: "卷" }
    ]
  },
  {
    label: "工程材料",
    options: [
      { label: "支", value: "支" },
      { label: "根", value: "根" },
      { label: "条", value: "条" },
      { label: "张", value: "张" },
      { label: "片", value: "片" },
      { label: "盘", value: "盘" },
      { label: "对", value: "对" },
      { label: "路", value: "路" },
      { label: "口", value: "口" }
    ]
  },
  {
    label: "长度面积体积",
    options: [
      { label: "米", value: "米" },
      { label: "厘米", value: "厘米" },
      { label: "毫米", value: "毫米" },
      { label: "平方米", value: "平方米" },
      { label: "立方米", value: "立方米" }
    ]
  },
  {
    label: "重量容量",
    options: [
      { label: "千克", value: "千克" },
      { label: "克", value: "克" },
      { label: "吨", value: "吨" },
      { label: "升", value: "升" },
      { label: "毫升", value: "毫升" }
    ]
  },
  {
    label: "服务周期",
    options: [
      { label: "小时", value: "小时" },
      { label: "天", value: "天" },
      { label: "月", value: "月" },
      { label: "年", value: "年" }
    ]
  }
];
