package com.nineone.markdown.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@Configuration
public class FeignConfig {

    private static final List<String> USER_HEADERS = List.of(
            "X-User-Id", "X-User-Name", "X-User-Email", "X-User-Role", "X-User-Authorities"
    );

    @Bean
    public RequestInterceptor userContextRequestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    for (String header : USER_HEADERS) {
                        String value = request.getHeader(header);
                        if (value != null) {
                            template.header(header, value);
                        }
                    }
                }
            }
        };
    }
}
