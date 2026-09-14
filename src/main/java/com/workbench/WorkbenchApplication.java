package com.workbench;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.util.TimeZone;

@Slf4j
@SpringBootApplication
public class WorkbenchApplication {

    public static void main(String[] args) {
        // 多时区改造：JVM 默认时区固定为 UTC（须在 Spring 启动前设置，早于数据源连接）。
        // 让日志时间戳、pgjdbc 会话时区、依赖库的默认时间行为全局统一，不随部署机器漂移
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(WorkbenchApplication.class, args);
    }

    /** 启动完成后打印一行可复制的地址：服务 + Knife4j（仅 dev/profile 下有） */
    @EventListener(WebServerInitializedEvent.class)
    public void printStartupBanner(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        Environment env = event.getApplicationContext().getEnvironment();
        String base = "http://localhost:" + port;

        log.info("▸ 服务已启动: {}", base);
        if (Boolean.parseBoolean(env.getProperty("springdoc.api-docs.enabled", "true"))) {
            log.info("▸ API 文档(Knife4j): {}/doc.html", base);
            log.info("▸ Swagger UI(原生):   {}/swagger-ui/index.html", base);
            log.info("▸ OpenAPI JSON:       {}/v3/api-docs", base);
        }
    }
}
