package com.jiaruiblog.quickboxserver.storage.model;

import lombok.Data;

/**
 * 存储使用情况
 */
@Data
public class StorageUsage {
    /**
     * 存储名称
     */
    private String storageName;

    /**
     * 存储类型
     */
    private StorageType storageType;

    /**
     * 总空间（字节）
     */
    private Long totalSpace;

    /**
     * 已用空间（字节）
     */
    private Long usedSpace;

    /**
     * 可用空间（字节）
     */
    private Long availableSpace;

    /**
     * 文件数量
     */
    private Long fileCount;

    /**
     * 文件夹数量
     */
    private Long folderCount;

    /**
     * 会话数量
     */
    private Long sessionCount;

    /**
     * 最大文件大小（字节）
     */
    private Long maxFileSize;

    /**
     * 最小文件大小（字节）
     */
    private Long minFileSize;

    /**
     * 平均文件大小（字节）
     */
    private Double avgFileSize;

    /**
     * 已用空间百分比
     */
    private Double usedPercentage;

    /**
     * 可用空间百分比
     */
    private Double availablePercentage;

    /**
     * 计算百分比
     */
    public void calculatePercentages() {
        if (totalSpace != null && totalSpace > 0) {
            if (usedSpace != null) {
                usedPercentage = (usedSpace.doubleValue() / totalSpace.doubleValue()) * 100;
            }
            if (availableSpace != null) {
                availablePercentage = (availableSpace.doubleValue() / totalSpace.doubleValue()) * 100;
            }
        }

        if (fileCount != null && fileCount > 0 && usedSpace != null) {
            avgFileSize = usedSpace.doubleValue() / fileCount.doubleValue();
        }
    }

    /**
     * 获取总空间格式化字符串
     */
    public String getFormattedTotalSpace() {
        return formatBytes(totalSpace);
    }

    /**
     * 获取已用空间格式化字符串
     */
    public String getFormattedUsedSpace() {
        return formatBytes(usedSpace);
    }

    /**
     * 获取可用空间格式化字符串
     */
    public String getFormattedAvailableSpace() {
        return formatBytes(availableSpace);
    }

    /**
     * 获取平均文件大小格式化字符串
     */
    public String getFormattedAvgFileSize() {
        return formatBytes(avgFileSize != null ? avgFileSize.longValue() : null);
    }

    /**
     * 格式化字节大小
     */
    private String formatBytes(Long bytes) {
        if (bytes == null) {
            return "未知";
        }

        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes < 1024L * 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else {
            return String.format("%.2f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 检查是否空间不足
     */
    public boolean isSpaceLow(double thresholdPercent) {
        if (availablePercentage == null || totalSpace == null) {
            return false;
        }
        return availablePercentage < thresholdPercent;
    }

    /**
     * 检查是否空间严重不足
     */
    public boolean isSpaceCritical() {
        return isSpaceLow(10.0); // 低于10%为严重不足
    }

    /**
     * 检查是否空间警告
     */
    public boolean isSpaceWarning() {
        return isSpaceLow(20.0); // 低于20%为警告
    }

    /**
     * 获取剩余空间百分比
     */
    public Double getRemainingPercentage() {
        if (availablePercentage == null) {
            return null;
        }
        return availablePercentage;
    }

    /**
     * 获取已用空间百分比字符串
     */
    public String getFormattedUsedPercentage() {
        if (usedPercentage == null) {
            return "未知";
        }
        return String.format("%.2f%%", usedPercentage);
    }

    /**
     * 获取可用空间百分比字符串
     */
    public String getFormattedAvailablePercentage() {
        if (availablePercentage == null) {
            return "未知";
        }
        return String.format("%.2f%%", availablePercentage);
    }
}