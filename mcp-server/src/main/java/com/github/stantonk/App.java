// src/main/java/com/github/stantonk/App.java
package com.github.stantonk;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebMvc
public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static final WeatherGetter weatherGetter = new WeatherGetter();

    @Bean
    public HttpServletSseServerTransportProvider mcpTransportProvider() {
        return new HttpServletSseServerTransportProvider(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                "/mcp/message"
        );
    }

    @Bean
    public ServletRegistrationBean<HttpServletSseServerTransportProvider> mcpServletRegistration(
            HttpServletSseServerTransportProvider transportProvider) {

        log.info("Registering MCP Transport Provider as Servlet...");

        ServletRegistrationBean<HttpServletSseServerTransportProvider> registration =
                new ServletRegistrationBean<>(transportProvider, "/*");

        registration.setLoadOnStartup(1);
        registration.setName("mcpTransport");

        log.info("MCP Transport Provider registered as Servlet: {}", registration);
        return registration;
    }

    @Bean
    public McpSyncServer mcpServer(HttpServletSseServerTransportProvider transportProvider) {
//        // 创建传输提供者
//        HttpServletSseServerTransportProvider transportProvider =
//                new HttpServletSseServerTransportProvider(new ObjectMapper(), "/mcp/message");

        // 创建MCP服务器
        McpSyncServer syncServer = McpServer.sync(transportProvider)
                .serverInfo("mcp-springboot-server", "0.8.1")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .resources(true, true)     // Enable resource support
                        .tools(true)               // Enable tool support
                        .prompts(true)             // Enable prompt support
                        .logging()                 // Enable logging support
                        .build())
                .build();

        // 注册天气工具
        McpServerFeatures.SyncToolSpecification weatherTool = new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "weather",
                        "fetches weather from lat and long",
                        new McpSchema.JsonSchema(
                                "object",
                                Map.of("latitude", Map.of("type", "number", "minimum", -90, "maximum", 90),
                                        "longitude", Map.of("type", "number", "minimum", -180, "maximum", 180)),
                                List.of("latitude", "longitude"),
                                false)
                ),
                (exchange, arguments) -> {
                    Double latitude = (Double) arguments.get("latitude");
                    Double longitude = (Double) arguments.get("longitude");

                    var weather = "Sorry, unable to fetch weather.";
                    try {
                        weather = weatherGetter.getForecast(latitude, longitude);
                    } catch (Exception e) {
                        log.error("Unable to retrieve weather data", e);
                        throw new RuntimeException(e);
                    }
                    return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(weather)), false);
                }
        );
        syncServer.addTool(weatherTool);

        // 发送日志通知
        syncServer.loggingNotification(McpSchema.LoggingMessageNotification.builder()
                .level(McpSchema.LoggingLevel.INFO)
                .logger("mcp-server")
                .data("Server initialized and ready to handle connections")
                .build());

        log.info("MCP Server info: {}", syncServer.getServerInfo());

        return syncServer;
    }

    @Bean
    public CommandLineRunner commandLineRunner(McpSyncServer mcpServer) {
        return args -> {
            log.info("MCP Server started successfully with Spring Boot!");
        };
    }
}