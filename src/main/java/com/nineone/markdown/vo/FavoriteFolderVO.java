package com.nineone.markdown.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 收藏夹分类展示对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteFolderVO {

    /**
     * 收藏夹 ID
     */
    private Long id;

    /**
     * 收藏夹名称
     */
    private String name;

    /**
     * 收藏夹描述
     */
    private String description;

    /**
     * 排序字段
     */
    private Integer sortOrder;

    /**
     * 该收藏夹下的文章数量
     */
    private Integer articleCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
