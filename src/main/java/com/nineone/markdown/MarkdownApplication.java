package com.nineone.markdown;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MarkdownApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarkdownApplication.class, args);
    }

}
