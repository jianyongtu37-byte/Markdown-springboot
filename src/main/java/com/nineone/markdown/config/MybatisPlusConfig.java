package com.nineone.markdown.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.nineone.markdown.handler.UserDataPermissionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置类
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataPermissionInterceptor dataPermissionInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 1. 添加数据权限插件（放在分页插件之前）
        // 注意：分页插件在生成COUNT语句时，数据权限插件可能被调用两次
        // 如果出现参数绑定问题，可以考虑使用@InterceptorIgnore局部绕过
        interceptor.addInnerInterceptor(dataPermissionInterceptor);
        
        // 2. 添加分页插件（指定数据库类型）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        
        return interceptor;
    }

    @Bean
    public DataPermissionInterceptor dataPermissionInterceptor(UserDataPermissionHandler dataPermissionHandler) {
        return new DataPermissionInterceptor(dataPermissionHandler);
    }
}
