package com.workbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class WorkbenchApplication {

    public static void main(String[] args) {
        // 多时区改造：JVM 默认时区固定为 UTC（须在 Spring 启动前设置，早于数据源连接）。
        // 让日志时间戳、pgjdbc 会话时区、依赖库的默认时间行为全局统一，不随部署机器漂移
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(WorkbenchApplication.class, args);
    }
}
