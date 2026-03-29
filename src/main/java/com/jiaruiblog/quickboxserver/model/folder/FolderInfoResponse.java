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
     * 下载URL
     */
    private String downloadUrl;

    /**
     * 文件列表
     */
    private List<FileInfo> files;

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
         * 下载URL
         */
        private String downloadUrl;
    }

    /**
     * 检查是否可下载
     */
    public boolean isDownloadable() {
        return !expired && !downloaded;
    }
}
