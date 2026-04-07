package com.yizhaoqi.smartpai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SmartPaiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartPaiApplication.class, args);
    }

}
