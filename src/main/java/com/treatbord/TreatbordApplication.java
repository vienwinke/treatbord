package com.treatbord;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Treatbord 任务接取平台启动类。
 */
@SpringBootApplication
@MapperScan("com.treatbord.module.**.mapper")
@EnableScheduling
public class TreatbordApplication {

    public static void main(String[] args) {
        SpringApplication.run(TreatbordApplication.class, args);
    }
}