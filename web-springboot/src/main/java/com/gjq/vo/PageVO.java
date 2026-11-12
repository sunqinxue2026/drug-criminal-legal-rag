package com.gjq.vo;

import lombok.Data;
import java.util.List;

/**
 * 分页响应VO
 */
@Data
public class PageVO<T> {
    
    private List<T> records;
    
    private Long total;
    
    private Integer size;
    
    private Integer current;
    
    private Integer pages;
    
    public PageVO() {
    }
    
    public PageVO(List<T> records, Long total, Integer size, Integer current) {
        this.records = records;
        this.total = total;
        this.size = size;
        this.current = current;
        this.pages = (int) Math.ceil((double) total / size);
    }
}
