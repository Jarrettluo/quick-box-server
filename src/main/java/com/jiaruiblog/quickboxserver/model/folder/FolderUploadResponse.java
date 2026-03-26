package com.jiaruiblog.quickboxserver.model.folder;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件夹上传响应
 */
@Data
public class FolderUploadResponse {
    /**
     * 上传会话ID
     */
    private String sessionId;

    /**
     * 文件夹ID
     */
    private String folderId;

    /**
     * 文件夹名称
     */
    private String folderName;

    /**
     * 取件码
     */
    private String accessCode;

    /**
     * 上传状态
     */
    private UploadStatus status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 总文件数
     */
    private Integer totalFiles;

    /**
     * 总大小（字节）
     */
    private Long totalSize;

    /**
     * 已上传文件数
     */
    private Integer uploadedFiles = 0;

    /**
     * 已上传大小（字节）
     */
    private Long uploadedSize = 0L;

    /**
     * 上传进度百分比
     */
    private Double progress = 0.0;

    /**
     * 预计剩余时间（秒）
     */
    private Long estimatedRemainingTime;

    /**
     * 上传速度（字节/秒）
     */
    private Long uploadSpeed;

    /**
     * 存储后端
     */
    private String storageBackend;

    /**
     * 文件夹路径
     */
    private String folderPath;

    /**
     * 下载URL
     */
    private String downloadUrl;

    /**
     * 信息URL
     */
    private String infoUrl;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 元数据
     */
    private String metadata;

    /**
     * 上传状态枚举
     */
    public enum UploadStatus {
        INITIALIZED("已初始化", "上传会话已创建"),
        UPLOADING("上传中", "文件正在上传"),
        MERGING("合并中", "文件分片正在合并"),
        COMPLETED("已完成", "上传完成"),
        FAILED("失败", "上传失败"),
        CANCELLED("已取消", "上传已取消");

        private final String name;
        private final String description;

        UploadStatus(String name, String description) {
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
     * 更新上传进度
     */
    public void updateProgress(int uploadedFiles, long uploadedSize) {
        this.uploadedFiles = uploadedFiles;
        this.uploadedSize = uploadedSize;

        if (totalFiles != null && totalFiles > 0) {
            this.progress = (uploadedFiles * 100.0) / totalFiles;
        } else if (totalSize != null && totalSize > 0) {
            this.progress = (uploadedSize * 100.0) / totalSize;
        } else {
            this.progress = 0.0;
        }

        // 限制进度在0-100之间
        this.progress = Math.max(0.0, Math.min(100.0, this.progress));
    }

    /**
     * 获取格式化的大小
     */
    public String getFormattedTotalSize() {
        if (totalSize == null) {
            return "0 B";
        }

        if (totalSize < 1024) {
            return totalSize + " B";
        } else if (totalSize < 1024 * 1024) {
            return String.format("%.2f KB", totalSize / 1024.0);
        } else if (totalSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", totalSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", totalSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取格式化的已上传大小
     */
    public String getFormattedUploadedSize() {
        if (uploadedSize == null) {
            return "0 B";
        }

        if (uploadedSize < 1024) {
            return uploadedSize + " B";
        } else if (uploadedSize < 1024 * 1024) {
            return String.format("%.2f KB", uploadedSize / 1024.0);
        } else if (uploadedSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", uploadedSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", uploadedSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取格式化的上传速度
     */
    public String getFormattedUploadSpeed() {
        if (uploadSpeed == null) {
            return "0 B/s";
        }

        if (uploadSpeed < 1024) {
            return uploadSpeed + " B/s";
        } else if (uploadSpeed < 1024 * 1024) {
            return String.format("%.2f KB/s", uploadSpeed / 1024.0);
        } else if (uploadSpeed < 1024 * 1024 * 1024) {
            return String.format("%.2f MB/s", uploadSpeed / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB/s", uploadSpeed / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取格式化的剩余时间
     */
    public String getFormattedRemainingTime() {
        if (estimatedRemainingTime == null) {
            return "未知";
        }

        if (estimatedRemainingTime < 60) {
            return estimatedRemainingTime + "秒";
        } else if (estimatedRemainingTime < 3600) {
            long minutes = estimatedRemainingTime / 60;
            long seconds = estimatedRemainingTime % 60;
            return minutes + "分" + seconds + "秒";
        } else {
            long hours = estimatedRemainingTime / 3600;
            long minutes = (estimatedRemainingTime % 3600) / 60;
            return hours + "小时" + minutes + "分";
        }
    }

    /**
     * 获取进度百分比字符串
     */
    public String getProgressPercentage() {
        return String.format("%.2f%%", progress);
    }

    /**
     * 检查是否完成
     */
    public boolean isCompleted() {
        return status == UploadStatus.COMPLETED;
    }

    /**
     * 检查是否失败
     */
    public boolean isFailed() {
        return status == UploadStatus.FAILED;
    }

    /**
     * 检查是否进行中
     */
    public boolean isInProgress() {
        return status == UploadStatus.UPLOADING || status == UploadStatus.MERGING;
    }

    /**
     * 获取剩余文件数
     */
    public Integer getRemainingFiles() {
        if (totalFiles == null || uploadedFiles == null) {
            return null;
        }
        return Math.max(0, totalFiles - uploadedFiles);
    }

    /**
     * 获取剩余大小
     */
    public Long getRemainingSize() {
        if (totalSize == null || uploadedSize == null) {
            return null;
        }
        return Math.max(0, totalSize - uploadedSize);
    }

    /**
     * 获取平均文件大小
     */
    public Double getAverageFileSize() {
        if (totalFiles == null || totalFiles == 0 || totalSize == null) {
            return 0.0;
        }
        return totalSize.doubleValue() / totalFiles.doubleValue();
    }

    /**
     * 获取预计完成时间
     */
    public LocalDateTime getEstimatedCompletionTime() {
        if (estimatedRemainingTime == null) {
            return null;
        }
        return LocalDateTime.now().plusSeconds(estimatedRemainingTime);
    }
}