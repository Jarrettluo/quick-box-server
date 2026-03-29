package com.jiaruiblog.quickboxserver.model.folder;

import lombok.Data;

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
     * 过期时间（秒）
     */
    private Integer expireSeconds = 604800; // 7天

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

        if (expireSeconds != null && expireSeconds < 60) {
            throw new IllegalArgumentException("过期时间必须至少60秒");
        }
    }
}
