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
     * 已上传大小（字节）
     */
    private Long uploadedSize = 0L;

    /**
     * 上传进度百分比
     */
    private Double progress = 0.0;

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
     * 上传状态枚举
     */
    public enum UploadStatus {
        INITIALIZED("已初始化"),
        UPLOADING("上传中"),
        MERGING("合并中"),
        COMPLETED("已完成"),
        FAILED("失败"),
        CANCELLED("已取消");

        private final String name;

        UploadStatus(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * 更新上传进度
     */
    public void updateProgress(long uploadedSize) {
        this.uploadedSize = uploadedSize;

        if (totalSize != null && totalSize > 0) {
            this.progress = (uploadedSize * 100.0) / totalSize;
            this.progress = Math.max(0.0, Math.min(100.0, this.progress));
        }
    }
}
