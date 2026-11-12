package com.gjq;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 法律咨询智能问答系统主启动类
 */
@SpringBootApplication
@MapperScan("com.gjq.mapper")
public class LegalRagApplication {
    public static void main(String[] args) {
        SpringApplication.run(LegalRagApplication.class, args);
        System.out.println("========================================");
        System.out.println("法律咨询智能问答系统启动成功！");
        System.out.println("API文档地址: http://localhost:8080/api/swagger-ui.html");
        System.out.println("========================================");
    }
}
