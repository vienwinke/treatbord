package com.treatbord.common;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应（docs/API_DESIGN.md §1.3）。
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> list;
    private long total;
    private long page;
    private long pageSize;

    public static <T> PageResult<T> of(List<T> list, long total, long page, long pageSize) {
        PageResult<T> r = new PageResult<>();
        r.list = list == null ? Collections.emptyList() : list;
        r.total = total;
        r.page = page;
        r.pageSize = pageSize;
        return r;
    }
}