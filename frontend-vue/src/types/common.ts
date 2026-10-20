export interface ApiResult<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export interface PageResult<T> {
  pageNo: number;
  pageSize: number;
  total: number;
  records: T[];
}

export interface PageQuery {
  pageNo: number;
  pageSize: number;
}
