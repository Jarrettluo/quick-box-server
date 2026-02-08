package com.jiaruiblog.quickboxserver.storage.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件信息
 */
@Data
public class FileInfo {
    /**
     * 文件ID
     */
    private String fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * MIME类型
     */
    private String mimeType;

    /**
     * MD5哈希值
     */
    private String md5Hash;

    /**
     * SHA256哈希值
     */
    private String sha256Hash;

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
     * 元数据
     */
    private String metadataJson;

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
     * 获取文件扩展名
     */
    public String getFileExtension() {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 获取文件名（不含扩展名）
     */
    public String getFileNameWithoutExtension() {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
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
     * 获取文件大小格式化字符串
     */
    public String getFormattedSize() {
        if (fileSize == null) {
            return "0 B";
        }

        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
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
}