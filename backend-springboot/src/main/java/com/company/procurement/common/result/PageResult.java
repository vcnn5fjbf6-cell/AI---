package com.company.procurement.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class PageResult<T> {

    private long pageNo;
    private long pageSize;
    private long total;
    private long totalPages;
    private List<T> records;

    public static <T> PageResult<T> of(long pageNo, long pageSize, long total, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.pageNo = pageNo;
        result.pageSize = pageSize;
        result.total = total;
        result.totalPages = pageSize <= 0 ? 0 : (long) Math.ceil((double) total / pageSize);
        result.records = records == null ? Collections.emptyList() : records;
        return result;
    }

    public static <T> PageResult<T> from(IPage<T> page) {
        return of(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords());
    }
}
