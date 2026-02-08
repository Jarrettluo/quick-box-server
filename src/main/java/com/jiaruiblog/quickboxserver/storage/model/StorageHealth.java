package com.jiaruiblog.quickboxserver.storage.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 存储健康状态
 */
@Data
public class StorageHealth {
    /**
     * 存储名称
     */
    private String storageName;

    /**
     * 存储类型
     */
    private StorageType storageType;

    /**
     * 健康状态
     */
    private HealthStatus status = HealthStatus.UNKNOWN;

    /**
     * 最后检查时间
     */
    private LocalDateTime lastCheckTime;

    /**
     * 响应时间（毫秒）
     */
    private Long responseTime;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 可用空间（字节）
     */
    private Long availableSpace;

    /**
     * 已用空间（字节）
     */
    private Long usedSpace;

    /**
     * 总空间（字节）
     */
    private Long totalSpace;

    /**
     * 连接数
     */
    private Integer connectionCount;

    /**
     * 失败次数
     */
    private Integer failureCount = 0;

    /**
     * 成功次数
     */
    private Integer successCount = 0;

    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        HEALTHY("健康", "存储服务运行正常"),
        DEGRADED("降级", "存储服务部分功能异常"),
        UNHEALTHY("不健康", "存储服务无法正常使用"),
        UNKNOWN("未知", "存储服务状态未知");

        private final String name;
        private final String description;

        HealthStatus(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 检查是否健康
     */
    public boolean isHealthy() {
        return status == HealthStatus.HEALTHY;
    }

    /**
     * 检查是否可用
     */
    public boolean isAvailable() {
        return status == HealthStatus.HEALTHY || status == HealthStatus.DEGRADED;
    }

    /**
     * 获取可用空间百分比
     */
    public Double getAvailableSpacePercentage() {
        if (totalSpace == null || totalSpace <= 0) {
            return null;
        }
        if (availableSpace == null) {
            return null;
        }
        return (availableSpace.doubleValue() / totalSpace.doubleValue()) * 100;
    }

    /**
     * 获取已用空间百分比
     */
    public Double getUsedSpacePercentage() {
        if (totalSpace == null || totalSpace <= 0) {
            return null;
        }
        if (usedSpace == null) {
            return null;
        }
        return (usedSpace.doubleValue() / totalSpace.doubleValue()) * 100;
    }
}