package com.jiaruiblog.quickboxserver.storage.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 存储统计信息
 */
@Data
public class StorageStats {
    /**
     * 存储名称
     */
    private String storageName;

    /**
     * 存储类型
     */
    private StorageType storageType;

    /**
     * 统计开始时间
     */
    private LocalDateTime startTime;

    /**
     * 统计结束时间
     */
    private LocalDateTime endTime;

    /**
     * 文件总数
     */
    private Long totalFiles = 0L;

    /**
     * 文件夹总数
     */
    private Long totalFolders = 0L;

    /**
     * 总文件大小（字节）
     */
    private Long totalSize = 0L;

    /**
     * 上传操作次数
     */
    private Long uploadCount = 0L;

    /**
     * 下载操作次数
     */
    private Long downloadCount = 0L;

    /**
     * 删除操作次数
     */
    private Long deleteCount = 0L;

    /**
     * 成功操作次数
     */
    private Long successCount = 0L;

    /**
     * 失败操作次数
     */
    private Long failureCount = 0L;

    /**
     * 平均上传时间（毫秒）
     */
    private Double avgUploadTime = 0.0;

    /**
     * 平均下载时间（毫秒）
     */
    private Double avgDownloadTime = 0.0;

    /**
     * 最大上传文件大小（字节）
     */
    private Long maxUploadFileSize = 0L;

    /**
     * 最小上传文件大小（字节）
     */
    private Long minUploadFileSize = Long.MAX_VALUE;

    /**
     * 并发上传数
     */
    private Integer concurrentUploads = 0;

    /**
     * 并发下载数
     */
    private Integer concurrentDownloads = 0;

    /**
     * 网络传输总量（字节）
     */
    private Long networkTransferred = 0L;

    /**
     * 缓存命中率
     */
    private Double cacheHitRate = 0.0;

    /**
     * 错误率
     */
    public Double getErrorRate() {
        long totalOperations = successCount + failureCount;
        if (totalOperations == 0) {
            return 0.0;
        }
        return (failureCount.doubleValue() / totalOperations) * 100;
    }

    /**
     * 获取成功率
     */
    public Double getSuccessRate() {
        long totalOperations = successCount + failureCount;
        if (totalOperations == 0) {
            return 100.0;
        }
        return (successCount.doubleValue() / totalOperations) * 100;
    }

    /**
     * 获取平均文件大小
     */
    public Double getAvgFileSize() {
        if (totalFiles == 0) {
            return 0.0;
        }
        return totalSize.doubleValue() / totalFiles.doubleValue();
    }

    /**
     * 获取吞吐量（字节/秒）
     */
    public Double getThroughput() {
        if (startTime == null || endTime == null) {
            return 0.0;
        }
        long seconds = java.time.Duration.between(startTime, endTime).getSeconds();
        if (seconds == 0) {
            return networkTransferred.doubleValue();
        }
        return networkTransferred.doubleValue() / seconds;
    }
}