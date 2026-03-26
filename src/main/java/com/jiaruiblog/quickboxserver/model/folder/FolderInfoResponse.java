package com.jiaruiblog.quickboxserver.model.folder;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件夹信息响应
 */
@Data
public class FolderInfoResponse {
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
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 剩余过期时间（秒）
     */
    private Long remainingExpireSeconds;

    /**
     * 是否已过期
     */
    private Boolean expired = false;

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
     * 存储后端
     */
    private String storageBackend;

    /**
     * 文件夹路径
     */
    private String folderPath;

    /**
     * 是否保持目录结构
     */
    private Boolean keepStructure = true;

    /**
     * 目录结构JSON
     */
    private String structureJson;

    /**
     * 文件列表
     */
    private List<FileInfo> files;

    /**
     * 子文件夹列表
     */
    private List<SubfolderInfo> subfolders;

    /**
     * 下载URL
     */
    private String downloadUrl;

    /**
     * 预览URL（如果有）
     */
    private String previewUrl;

    /**
     * 是否可预览
     */
    private Boolean previewable = false;

    /**
     * 元数据
     */
    private String metadata;

    /**
     * 文件信息
     */
    @Data
    public static class FileInfo {
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
         * 文件扩展名
         */
        private String fileExtension;

        /**
         * 修改时间
         */
        private LocalDateTime modifyTime;

        /**
         * 是否可预览
         */
        private Boolean previewable = false;

        /**
         * 预览URL
         */
        private String previewUrl;

        /**
         * 下载URL
         */
        private String downloadUrl;

        /**
         * 获取格式化的大小
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
         * 获取图标类型
         */
        public String getIconType() {
            if (fileExtension == null) {
                return "file";
            }

            switch (fileExtension.toLowerCase()) {
                case "pdf":
                    return "pdf";
                case "doc":
                case "docx":
                    return "word";
                case "xls":
                case "xlsx":
                    return "excel";
                case "ppt":
                case "pptx":
                    return "powerpoint";
                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                case "bmp":
                case "svg":
                    return "image";
                case "mp4":
                case "avi":
                case "mov":
                case "wmv":
                case "flv":
                    return "video";
                case "mp3":
                case "wav":
                case "flac":
                case "aac":
                    return "audio";
                case "zip":
                case "rar":
                case "7z":
                case "tar":
                case "gz":
                    return "archive";
                case "txt":
                case "md":
                case "log":
                    return "text";
                case "html":
                case "htm":
                case "xml":
                case "json":
                case "yaml":
                case "yml":
                    return "code";
                default:
                    return "file";
            }
        }
    }

    /**
     * 子文件夹信息
     */
    @Data
    public static class SubfolderInfo {
        /**
         * 文件夹名
         */
        private String folderName;

        /**
         * 相对路径
         */
        private String relativePath;

        /**
         * 文件数
         */
        private Integer fileCount;

        /**
         * 子文件夹数
         */
        private Integer subfolderCount;

        /**
         * 总大小（字节）
         */
        private Long totalSize;

        /**
         * 修改时间
         */
        private LocalDateTime modifyTime;

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
         * 获取总项目数
         */
        public Integer getTotalItems() {
            int total = 0;
            if (fileCount != null) {
                total += fileCount;
            }
            if (subfolderCount != null) {
                total += subfolderCount;
            }
            return total;
        }
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
     * 获取剩余过期时间描述
     */
    public String getRemainingExpireDescription() {
        if (remainingExpireSeconds == null) {
            return "永不过期";
        }

        if (remainingExpireSeconds <= 0) {
            return "已过期";
        }

        if (remainingExpireSeconds < 60) {
            return remainingExpireSeconds + "秒";
        } else if (remainingExpireSeconds < 3600) {
            return (remainingExpireSeconds / 60) + "分钟";
        } else if (remainingExpireSeconds < 86400) {
            return (remainingExpireSeconds / 3600) + "小时";
        } else {
            return (remainingExpireSeconds / 86400) + "天";
        }
    }

    /**
     * 获取总项目数
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

    /**
     * 检查是否可下载
     */
    public boolean isDownloadable() {
        return !expired && !downloaded;
    }

    /**
     * 检查是否可预览
     */
    public boolean isPreviewable() {
        return previewable != null && previewable;
    }

    /**
     * 获取文件夹深度
     */
    public Integer getFolderDepth() {
        if (structureJson == null) {
            return 1;
        }
        // TODO: 从结构JSON中计算深度
        return 1;
    }
}