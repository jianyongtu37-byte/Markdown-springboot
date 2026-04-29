package com.nineone.markdown.config;

import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

/**
 * Elasticsearch 配置类
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.elasticsearch.repositories.enabled", havingValue = "true", matchIfMissing = true)
@EnableElasticsearchRepositories(basePackages = "com.nineone.markdown.repository.es")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.rest.uris:http://localhost:9200}")
    private String elasticsearchUris;

    @Value("${spring.elasticsearch.rest.username:}")
    private String username;

    @Value("${spring.elasticsearch.rest.password:}")
    private String password;

    @Override
    public ClientConfiguration clientConfiguration() {
        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder = ClientConfiguration.builder()
                .connectedTo(elasticsearchUris.replace("http://", "").replace("https://", "").split(","));

        // 如果配置了用户名和密码，则添加基本认证
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            builder.withBasicAuth(username, password);
        }

        return builder.build();
    }

    /**
     * 覆盖父类的 jsonpMapper() 方法，提供自定义的 JsonpMapper
     * 注册 JavaTimeModule 以支持 Java 8 时间类型，并禁用日期时间戳序列化
     */
    @Override
    public JsonpMapper jsonpMapper() {
        // 创建自定义的 ObjectMapper 并注册 JavaTimeModule
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 使用自定义的 ObjectMapper 创建 JacksonJsonpMapper
        return new JacksonJsonpMapper(objectMapper);
    }
}
