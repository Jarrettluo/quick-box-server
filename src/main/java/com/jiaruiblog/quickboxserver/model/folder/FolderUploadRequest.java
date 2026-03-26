package com.jiaruiblog.quickboxserver.model.folder;

import lombok.Data;

import java.util.List;

/**
 * 文件夹上传请求
 */
@Data
public class FolderUploadRequest {
    /**
     * 文件夹名称
     */
    private String folderName;

    /**
     * 总文件数
     */
    private Integer totalFiles;

    /**
     * 总大小（字节）
     */
    private Long totalSize;

    /**
     * 目录结构JSON
     */
    private String structureJson;

    /**
     * 是否自动打包为ZIP
     */
    private Boolean autoZip = true;

    /**
     * 是否保持目录结构
     */
    private Boolean keepStructure = true;

    /**
     * 文件列表（用于普通文件夹上传）
     */
    private List<FileItem> files;

    /**
     * 元数据
     */
    private String metadata;

    /**
     * 过期时间（秒）
     */
    private Integer expireSeconds = 604800; // 7天

    /**
     * 文件项
     */
    @Data
    public static class FileItem {
        /**
         * 文件名
         */
        private String fileName;

        /**
         * 文件大小（字节）
         */
        private Long fileSize;

        /**
         * 相对路径
         */
        private String relativePath;

        /**
         * MIME类型
         */
        private String mimeType;

        /**
         * MD5哈希值
         */
        private String md5Hash;

        /**
         * 分片大小（字节）
         */
        private Long chunkSize = 16 * 1024 * 1024L; // 16MB

        /**
         * 总分片数
         */
        private Integer totalChunks;
    }

    /**
     * 验证请求参数
     */
    public void validate() {
        if (folderName == null || folderName.trim().isEmpty()) {
            throw new IllegalArgumentException("文件夹名称不能为空");
        }

        if (totalFiles == null || totalFiles <= 0) {
            throw new IllegalArgumentException("总文件数必须大于0");
        }

        if (totalSize == null || totalSize <= 0) {
            throw new IllegalArgumentException("总大小必须大于0");
        }

        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("文件列表不能为空");
        }

        // 验证文件项
        for (FileItem file : files) {
            if (file.getFileName() == null || file.getFileName().trim().isEmpty()) {
                throw new IllegalArgumentException("文件名不能为空");
            }
            if (file.getFileSize() == null || file.getFileSize() <= 0) {
                throw new IllegalArgumentException("文件大小必须大于0");
            }
            if (file.getRelativePath() == null) {
                throw new IllegalArgumentException("相对路径不能为空");
            }
        }

        if (expireSeconds != null && expireSeconds < 60) {
            throw new IllegalArgumentException("过期时间必须至少60秒");
        }
    }

    /**
     * 获取安全的文件夹名称
     */
    public String getSafeFolderName() {
        if (folderName == null) {
            return "unnamed_folder";
        }

        // 移除非法字符
        String safeName = folderName.replaceAll("[\\\\/:*?\"<>|]", "_");

        // 限制长度
        if (safeName.length() > 255) {
            safeName = safeName.substring(0, 255);
        }

        return safeName;
    }

    /**
     * 获取格式化的大小
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
     * 获取过期时间描述
     */
    public String getExpireDescription() {
        if (expireSeconds == null) {
            return "永不过期";
        }

        if (expireSeconds < 60) {
            return expireSeconds + "秒";
        } else if (expireSeconds < 3600) {
            return (expireSeconds / 60) + "分钟";
        } else if (expireSeconds < 86400) {
            return (expireSeconds / 3600) + "小时";
        } else {
            return (expireSeconds / 86400) + "天";
        }
    }
}