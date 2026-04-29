package com.nineone.markdown.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    
    /**
     * 当前页码
     */
    private Integer pageNum;
    
    /**
     * 每页大小
     */
    private Integer pageSize;
    
    /**
     * 总记录数
     */
    private Long total;
    
    /**
     * 总页数
     */
    private Integer totalPages;
    
    /**
     * 当前页数据列表
     */
    private List<T> list;
    
    /**
     * 是否有上一页
     */
    private Boolean hasPrevious;
    
    /**
     * 是否有下一页
     */
    private Boolean hasNext;
    
    /**
     * 是否是第一页
     */
    private Boolean isFirst;
    
    /**
     * 是否是最后一页
     */
    private Boolean isLast;
    
    /**
     * 创建分页结果对象
     * @param pageNum 当前页码
     * @param pageSize 每页大小
     * @param total 总记录数
     * @param list 当前页数据列表
     * @return 分页结果对象
     */
    public static <T> PageResult<T> of(Integer pageNum, Integer pageSize, Long total, List<T> list) {
        // 计算总页数
        int totalPages = (int) Math.ceil((double) total / pageSize);
        
        // 计算是否有上一页/下一页
        boolean hasPrevious = pageNum > 1;
        boolean hasNext = pageNum < totalPages;
        boolean isFirst = pageNum == 1;
        boolean isLast = pageNum.equals(totalPages) || totalPages == 0;
        
        return PageResult.<T>builder()
                .pageNum(pageNum)
                .pageSize(pageSize)
                .total(total)
                .totalPages(totalPages)
                .list(list)
                .hasPrevious(hasPrevious)
                .hasNext(hasNext)
                .isFirst(isFirst)
                .isLast(isLast)
                .build();
    }
}