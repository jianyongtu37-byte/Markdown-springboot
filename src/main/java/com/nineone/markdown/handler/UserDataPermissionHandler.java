package com.nineone.markdown.handler;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.nineone.markdown.enums.UserRoleEnum;
import com.nineone.markdown.util.UserContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;

/**
 * 用户数据权限处理器
 * 基于 MyBatis-Plus 的 DataPermissionInterceptor 插件
 * 自动为 SQL 注入用户权限过滤条件
 * 
 * 支持特性：
 * 1. 管理员（ROLE_ADMIN）可以查看所有数据（返回null，不注入条件）
 * 2. 普通用户只能查看自己的数据和系统公共数据（category表user_id=0）
 * 3. 未登录用户不能查看需要权限的数据（返回null，实际业务层应控制公开数据访问）
 */
@Component
public class UserDataPermissionHandler implements MultiDataPermissionHandler {

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // 1. 获取当前表名
        String tableName = table.getName();

        // 2. 只有特定表才需要进行数据隔离
        // 注意：表名可能是带反引号的，例如 `article`，我们需要去掉反引号
        tableName = tableName.replace("`", "");
        if (!"article".equals(tableName) && !"category".equals(tableName)) {
            return null; // 返回 null 代表放行，不修改原 SQL
        }

        // 3. 检查当前用户是否是管理员
        if (UserContextHolder.isAdmin()) {
            // 管理员可以查看所有数据，不注入任何条件
            return null;
        }

        // 4. 获取当前登录用户的 ID 
        Long currentUserId = UserContextHolder.getUserId();
        
        // 如果未登录，返回 null 表示不注入条件
        // 注意：未登录用户的访问应在业务层控制（如只能访问公开文章）
        if (currentUserId == null) {
            return null; 
        }

        // 5. 构造基础条件： user_id = 当前用户ID
        EqualsTo userEquals = new EqualsTo();
        userEquals.setLeftExpression(new Column(table, "user_id"));
        userEquals.setRightExpression(new LongValue(currentUserId));

        Expression dataPermissionCondition = userEquals;

        // 6. 针对 category 表的特殊处理：(user_id = 0 OR user_id = 当前用户ID)
        if ("category".equals(tableName)) {
            EqualsTo publicEquals = new EqualsTo();
            publicEquals.setLeftExpression(new Column(table, "user_id"));
            publicEquals.setRightExpression(new LongValue(0)); // 0 代表系统公共分类

            // 组合 OR 条件
            OrExpression orExpression = new OrExpression(publicEquals, userEquals);
            // 加上括号，防止和原有 SQL 的 AND 逻辑混淆
            dataPermissionCondition = new Parenthesis(orExpression);
        }

        // 7. 直接返回权限条件，千万不要自己和 where 去 AND 拼接！框架会处理的。
        // （如果原 SQL 没有 WHERE 条件，框架会自动将这个条件作为初始 WHERE 条件）
        return dataPermissionCondition;
    }
}
