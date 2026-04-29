package com.nineone.markdown.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文章状态枚举
 * 0 = 草稿 (DRAFT)
 * 1 = 仅自己可见 (PRIVATE)
 * 2 = 公开可见 (PUBLIC)
 */
@Getter
@AllArgsConstructor
public enum ArticleStatusEnum {

    DRAFT(0, "草稿"),
    PRIVATE(1, "仅自己可见"),
    PUBLIC(2, "公开可见");

    @EnumValue
    @JsonValue  // 加上这个！让 Jackson 给前端返回 JSON 时也输出 0/1/2
    private final Integer code;
    private final String description;

    /**
     * 根据 code 获取枚举
     */
    public static ArticleStatusEnum of(Integer code) {
        if (code == null) {
            return DRAFT;
        }
        for (ArticleStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return DRAFT;
    }

    /**
     * 是否为公开可见
     */
    public boolean isPublic() {
        return this == PUBLIC;
    }

    /**
     * 是否为私有（仅自己可见）
     */
    public boolean isPrivate() {
        return this == PRIVATE;
    }

    /**
     * 是否为草稿
     */
    public boolean isDraft() {
        return this == DRAFT;
    }

    /**
     * 是否可以公开访问（仅公开文章可被非作者访问）
     */
    public boolean canPublicAccess() {
        return this == PUBLIC;
    }

    /**
     * 是否为已发布状态（私有或公开都算已发布）
     */
    public boolean isPublished() {
        return this == PRIVATE || this == PUBLIC;
    }
}