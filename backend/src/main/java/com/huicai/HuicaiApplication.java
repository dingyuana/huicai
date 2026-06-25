package com.huicai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HuicaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HuicaiApplication.class, args);
    }
}