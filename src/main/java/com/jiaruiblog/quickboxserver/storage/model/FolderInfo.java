package com.jiaruiblog.quickboxserver.storage.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件夹信息
 */
@Data
public class FolderInfo {
    /**
     * 文件夹ID
     */
    private String folderId;

    /**
     * 文件夹名
     */
    private String folderName;

    /**
     * 文件夹路径
     */
    private String folderPath;

    /**
     * 总文件数
     */
    private Integer totalFiles;

    /**
     * 总文件夹数
     */
    private Integer totalFolders;

    /**
     * 总大小（字节）
     */
    private Long totalSize;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    private LocalDateTime modifyTime;

    /**
     * 访问时间
     */
    private LocalDateTime accessTime;

    /**
     * 上传会话ID
     */
    private String uploadSessionId;

    /**
     * 取件码
     */
    private String accessCode;

    /**
     * 是否已下载
     */
    private Boolean downloaded = false;

    /**
     * 下载次数
     */
    private Integer downloadCount = 0;

    /**
     * 最后下载时间
     */
    private LocalDateTime lastDownloadTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 目录结构JSON
     */
    private String structureJson;

    /**
     * 是否自动打包为ZIP
     */
    private Boolean autoZip = true;

    /**
     * 存储后端
     */
    private String storageBackend;

    /**
     * 是否有效
     */
    private Boolean valid = true;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 文件列表（仅用于展示）
     */
    private List<FileItem> files;

    /**
     * 子文件夹列表（仅用于展示）
     */
    private List<FolderItem> subfolders;

    /**
     * 文件项
     */
    @Data
    public static class FileItem {
        private String name;
        private String path;
        private Long size;
        private String mimeType;
        private LocalDateTime modifyTime;
    }

    /**
     * 文件夹项
     */
    @Data
    public static class FolderItem {
        private String name;
        private String path;
        private Integer fileCount;
        private Long totalSize;
        private LocalDateTime modifyTime;
    }

    /**
     * 检查是否过期
     */
    public boolean isExpired() {
        if (expireTime == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 获取总大小格式化字符串
     */
    public String getFormattedSize() {
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
     * 获取剩余过期时间（秒）
     */
    public Long getRemainingExpireSeconds() {
        if (expireTime == null) {
            return null;
        }
        java.time.Duration duration = java.time.Duration.between(LocalDateTime.now(), expireTime);
        return duration.getSeconds();
    }

    /**
     * 获取总项目数（文件+文件夹）
     */
    public Integer getTotalItems() {
        int total = 0;
        if (totalFiles != null) {
            total += totalFiles;
        }
        if (totalFolders != null) {
            total += totalFolders;
        }
        return total;
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
}