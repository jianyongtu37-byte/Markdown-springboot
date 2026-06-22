package com.nineone.markdown.client;

import com.nineone.markdown.client.fallback.ExportServiceClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "markdown-export", path = "/api", fallbackFactory = ExportServiceClientFallbackFactory.class)
public interface ExportServiceClient {

    @PostMapping("/export/pdf")
    byte[] exportPdf(@RequestBody Map<String, Object> request,
                     @RequestHeader("X-User-Id") Long userId);

    @PostMapping("/export/word")
    byte[] exportWord(@RequestBody Map<String, Object> request,
                      @RequestHeader("X-User-Id") Long userId);

    @PostMapping("/export/markdown-zip")
    byte[] exportMarkdownZip(@RequestBody Map<String, Object> request,
                             @RequestHeader("X-User-Id") Long userId);
}
