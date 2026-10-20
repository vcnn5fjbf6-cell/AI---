import type { HrEmployee } from "../../stores/hr";

export const SAMPLE_EMPLOYEES: Array<Partial<HrEmployee>> = [
  { name: "王建国", gender: "男", department: "交付运维部", position: "交付运维主管", company: "世纪互联", salary: 18000 },
  { name: "刘伟", gender: "男", department: "交付运维部", position: "交付运维主管", company: "世纪互联", salary: 18000 },
  { name: "陈志强", gender: "男", department: "设施运维部", position: "设施运维主管", company: "世纪互联", salary: 18000 },
  { name: "赵磊", gender: "男", department: "设施运维部", position: "设施运维值班长", company: "世纪互联", salary: 12000 },
  { name: "孙海", gender: "男", department: "设施运维部", position: "设施运维值班长", company: "世纪互联", salary: 12000 },
  { name: "周涛", gender: "男", department: "交付运维部", position: "高级交付运维工程师", company: "世纪互联", salary: 15000 },
  { name: "吴斌", gender: "男", department: "交付运维部", position: "高级交付运维工程师", company: "世纪互联", salary: 15000 },
  { name: "郑凯", gender: "男", department: "设施运维部", position: "高级交付运维工程师", company: "世纪互联", salary: 15000 },
  { name: "王强", gender: "男", department: "交付运维部", position: "交付运维工程师", company: "世纪互联", salary: 10000 },
  { name: "李杰", gender: "男", department: "交付运维部", position: "交付运维工程师", company: "世纪互联", salary: 10000 },
  { name: "张鹏", gender: "男", department: "交付运维部", position: "交付运维工程师", company: "世纪互联", salary: 10000 },
  { name: "刘洋", gender: "男", department: "交付运维部", position: "交付运维工程师", company: "世纪互联", salary: 10000 },
  { name: "杨帆", gender: "男", department: "交付运维部", position: "交付运维工程师", company: "世纪互联", salary: 10000 },
  { name: "苗壮壮", gender: "男", department: "交付运维部", position: "交付运维工程师", company: "亚康外包", salary: 10000 },
  { name: "孟泓利", gender: "男", department: "交付运维部", position: "交付运维工程师", company: "亚康外包", salary: 10000 },
  { name: "李广杰", gender: "男", department: "交付运维部", position: "交付运维工程师-网银专职", company: "亚康外包", salary: 10000 },
  { name: "运赛博", gender: "男", department: "交付运维部", position: "交付运维工程师-网银专职", company: "亚康外包", salary: 10000 },
  { name: "黄勇", gender: "男", department: "交付运维部", position: "交付运维值班工程师", company: "世纪互联", salary: 8000 },
  { name: "温海超", gender: "男", department: "交付运维部", position: "交付运维值班工程师", company: "亚康外包", salary: 8000 },
  { name: "杨佩涵", gender: "男", department: "交付运维部", position: "交付运维值班工程师", company: "亚康外包", salary: 8000 },
  { name: "尤龙州", gender: "男", department: "交付运维部", position: "交付运维值班工程师", company: "亚康外包", salary: 8000 },
  { name: "袁琦", gender: "男", department: "交付运维部", position: "交付运维值班工程师", company: "亚康外包", salary: 8000 },
  { name: "马超", gender: "男", department: "设施运维部", position: "设施运维工程师", company: "世纪互联", salary: 10000 },
  { name: "朱峰", gender: "男", department: "设施运维部", position: "设施运维工程师", company: "世纪互联", salary: 10000 },
  { name: "胡军", gender: "男", department: "设施运维部", position: "设施运维工程师", company: "世纪互联", salary: 10000 },
  { name: "林涛", gender: "男", department: "设施运维部", position: "设施运维值班工程师", company: "世纪互联", salary: 8000 },
  { name: "高翔", gender: "男", department: "设施运维部", position: "设施运维值班工程师", company: "世纪互联", salary: 8000 },
  { name: "罗浩", gender: "男", department: "设施运维部", position: "设施运维值班工程师", company: "世纪互联", salary: 8000 },
  { name: "李娜", gender: "女", department: "设施运维部", position: "前台", company: "世纪互联", salary: 6000 },
  { name: "王芳", gender: "女", department: "交付运维部", position: "前台", company: "世纪互联", salary: 6000 }
];

export function buildSampleEmployees(): HrEmployee[] {
  const now = new Date().toISOString();
  return SAMPLE_EMPLOYEES.map((sample, index) => ({
    id: index + 1,
    name: sample.name || "",
    gender: sample.gender || "男",
    department: sample.department || "交付运维部",
    position: sample.position || "",
    company: sample.company || "世纪互联",
    phone: `138${String(10000000 + index * 7919).slice(0, 8)}`,
    email: `hr${index + 1}@company.com`,
    joinDate: "2024-01-01",
    region: "华北一",
    room: "酒仙桥",
    building: "M6",
    salary: sample.salary,
    status: "在职",
    aiProfile: "",
    createdAt: now
  }));
}
