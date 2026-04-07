package com.yizhaoqi.smartpai.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Micrometer 指标配置类
 * 为 KnowFlow 定义自定义业务指标
 */
@Configuration
public class MetricsConfig {

    /**
     * 文件上传计数器
     * 记录成功上传的文件总数
     */
    @Bean
    public Counter fileUploadCounter(MeterRegistry registry) {
        return Counter.builder("knowflow_file_upload_total")
            .description("Total number of file uploads")
            .tag("type", "upload")
            .register(registry);
    }

    /**
     * 搜索耗时计时器
     * 记录搜索请求的耗时分布
     */
    @Bean
    public Timer searchTimer(MeterRegistry registry) {
        return Timer.builder("knowflow_search_duration_seconds")
            .description("Search request duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }

    /**
     * Kafka消息处理计数器
     * 记录成功处理的 Kafka 消息总数
     */
    @Bean
    public Counter kafkaProcessedCounter(MeterRegistry registry) {
        return Counter.builder("knowflow_kafka_messages_processed_total")
            .description("Total Kafka messages processed")
            .register(registry);
    }

    /**
     * WebSocket 活跃连接数
     * 使用 AtomicInteger 作为 Gauge 的数据源
     */
    @Bean
    public AtomicInteger activeWebSocketConnections(MeterRegistry registry) {
        AtomicInteger activeConnections = new AtomicInteger(0);
        Gauge.builder("knowflow_websocket_active_connections", activeConnections, AtomicInteger::get)
            .description("Number of active WebSocket connections")
            .register(registry);
        return activeConnections;
    }
}
