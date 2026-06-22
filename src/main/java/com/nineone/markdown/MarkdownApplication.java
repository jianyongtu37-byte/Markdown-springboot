package com.nineone.markdown;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class MarkdownApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(MarkdownApplication.class, args);
    }

    /**
     * 从项目根目录的 .env 文件加载环境变量到 System properties。
     * 已存在的环境变量不会被覆盖（环境变量优先级更高）。
     */
    private static void loadDotEnv() {
        Path envFile = findEnvFile();
        if (envFile == null) return;
        try (BufferedReader reader = Files.newBufferedReader(envFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 1) continue;
                String key = line.substring(0, eq).strip();
                String value = line.substring(eq + 1).strip();
                if (System.getProperty(key) == null && System.getenv(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("警告: 读取 .env 文件失败: " + e.getMessage());
        }
    }

    private static Path findEnvFile() {
        try {
            java.net.URL location = MarkdownApplication.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            Path classesDir = new java.io.File(location.toURI()).toPath();
            Path moduleDir = classesDir.getParent().getParent();
            if (moduleDir != null) {
                Path candidate = moduleDir.resolve(".env");
                if (Files.exists(candidate)) return candidate;
            }
        } catch (Exception ignored) {}
        Path cwd = Path.of(".env");
        if (Files.exists(cwd)) return cwd;
        return null;
    }
}
