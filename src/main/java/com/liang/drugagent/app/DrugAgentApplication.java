package com.liang.drugagent.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.liang.drugagent")
@MapperScan("com.liang.drugagent.scene.common.mapper")
public class DrugAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(DrugAgentApplication.class, args);
    }

}
