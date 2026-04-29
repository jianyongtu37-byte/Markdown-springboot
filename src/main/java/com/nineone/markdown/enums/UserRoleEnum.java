package com.nineone.markdown.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
@AllArgsConstructor
public enum UserRoleEnum {

    USER("ROLE_USER", "普通用户"),
    ADMIN("ROLE_ADMIN", "管理员");

    private final String roleName;
    private final String description;

    /**
     * 根据角色名称获取枚举
     */
    public static UserRoleEnum of(String roleName) {
        if (roleName == null) {
            return USER;
        }
        for (UserRoleEnum role : values()) {
            if (role.roleName.equals(roleName)) {
                return role;
            }
        }
        return USER;
    }

    /**
     * 检查是否是管理员
     */
    public static boolean isAdmin(String roleName) {
        return ADMIN.roleName.equals(roleName);
    }

    /**
     * 获取权限字符串
     */
    public String getAuthority() {
        return roleName;
    }
}