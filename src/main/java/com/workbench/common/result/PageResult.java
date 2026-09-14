package com.workbench.common.result;

import lombok.Data;

import java.util.List;

/**
 * 通用分页结果：{records, total, page, size, pages}
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private long page;

    /** 每页条数 */
    private long size;

    /** 总页数 */
    private long pages;

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(records);
        r.setTotal(total);
        r.setPage(page);
        r.setSize(size);
        r.setPages(size == 0 ? 0 : (total + size - 1) / size);
        return r;
    }
}
