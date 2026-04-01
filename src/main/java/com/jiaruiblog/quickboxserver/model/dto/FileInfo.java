package com.jiaruiblog.quickboxserver.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {
    private String filename;
    private long size;
    private long lastModified;
    private String downloadUrl;

    // 类型标识：file 或 folder
    private String type;

    // 文件夹专用字段（文件类型时为 null）
    private String folderName;
    private Integer totalFiles;
    private Long totalSize;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private Long remainingExpireSeconds;
    private Boolean expired;
    private Boolean downloaded;
    private List<FileDetail> files;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileDetail {
        private String fileName;
        private Long fileSize;
        private String relativePath;
        private String downloadUrl;
    }
}