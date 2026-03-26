package com.jiaruiblog.quickboxserver.model.folder;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件夹分片上传请求
 */
@Data
public class FolderChunkUploadRequest {
    /**
     * 上传会话ID
     */
    private String sessionId;

    /**
     * 分片序号
     */
    private Integer chunkNumber;

    /**
     * 总分片数
     */
    private Integer totalChunks;

    /**
     * 分片大小（字节）
     */
    private Long chunkSize;

    /**
     * 当前分片大小（字节）
     */
    private Long currentChunkSize;

    /**
     * 总大小（字节）
     */
    private Long totalSize;

    /**
     * 文件标识符（MD5）
     */
    private String identifier;

    /**
     * 文件名
     */
    private String filename;

    /**
     * 相对路径
     */
    private String relativePath;

    /**
     * 分片文件
     */
    private MultipartFile file;

    /**
     * 元数据
     */
    private String metadata;

    /**
     * 验证请求参数
     */
    public void validate() {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("上传会话ID不能为空");
        }

        if (chunkNumber == null || chunkNumber < 1) {
            throw new IllegalArgumentException("分片序号无效");
        }

        if (totalChunks == null || totalChunks <= 0) {
            throw new IllegalArgumentException("总分片数必须大于0");
        }

        if (chunkNumber > totalChunks) {
            throw new IllegalArgumentException("分片序号不能大于总分片数");
        }

        if (chunkSize == null || chunkSize <= 0) {
            throw new IllegalArgumentException("分片大小必须大于0");
        }

        if (currentChunkSize == null || currentChunkSize <= 0) {
            throw new IllegalArgumentException("当前分片大小必须大于0");
        }

        if (totalSize == null || totalSize <= 0) {
            throw new IllegalArgumentException("总大小必须大于0");
        }

        if (identifier == null || identifier.trim().isEmpty()) {
            throw new IllegalArgumentException("文件标识符不能为空");
        }

        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("分片文件不能为空");
        }

        if (file.getSize() != currentChunkSize) {
            throw new IllegalArgumentException("分片文件大小不匹配");
        }

        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("相对路径不能为空");
        }
    }

    /**
     * 获取分片进度百分比
     */
    public Double getChunkProgress() {
        if (totalChunks == null || totalChunks <= 0) {
            return 0.0;
        }
        return (chunkNumber * 100.0) / totalChunks;
    }

    /**
     * 获取总进度百分比
     */
    public Double getTotalProgress() {
        if (totalSize == null || totalSize <= 0) {
            return 0.0;
        }

        long uploadedSize = (long) (chunkNumber - 1) * chunkSize + currentChunkSize;
        return (uploadedSize * 100.0) / totalSize;
    }

    /**
     * 获取已上传大小
     */
    public Long getUploadedSize() {
        return (long) (chunkNumber - 1) * chunkSize + currentChunkSize;
    }

    /**
     * 获取剩余大小
     */
    public Long getRemainingSize() {
        if (totalSize == null) {
            return 0L;
        }
        return Math.max(0, totalSize - getUploadedSize());
    }

    /**
     * 获取剩余分片数
     */
    public Integer getRemainingChunks() {
        if (totalChunks == null) {
            return 0;
        }
        return Math.max(0, totalChunks - chunkNumber);
    }

    /**
     * 检查是否是最后一个分片
     */
    public boolean isLastChunk() {
        if (totalChunks == null) {
            return false;
        }
        return chunkNumber == totalChunks;
    }

    /**
     * 获取格式化的分片大小
     */
    public String getFormattedChunkSize() {
        if (chunkSize == null) {
            return "0 B";
        }

        if (chunkSize < 1024) {
            return chunkSize + " B";
        } else if (chunkSize < 1024 * 1024) {
            return String.format("%.2f KB", chunkSize / 1024.0);
        } else if (chunkSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", chunkSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", chunkSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取格式化的当前分片大小
     */
    public String getFormattedCurrentChunkSize() {
        if (currentChunkSize == null) {
            return "0 B";
        }

        if (currentChunkSize < 1024) {
            return currentChunkSize + " B";
        } else if (currentChunkSize < 1024 * 1024) {
            return String.format("%.2f KB", currentChunkSize / 1024.0);
        } else if (currentChunkSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", currentChunkSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", currentChunkSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取格式化的总大小
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
        long uploadedSize = getUploadedSize();
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
     * 获取格式化的剩余大小
     */
    public String getFormattedRemainingSize() {
        long remainingSize = getRemainingSize();
        if (remainingSize < 1024) {
            return remainingSize + " B";
        } else if (remainingSize < 1024 * 1024) {
            return String.format("%.2f KB", remainingSize / 1024.0);
        } else if (remainingSize < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", remainingSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", remainingSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取分片进度百分比字符串
     */
    public String getChunkProgressPercentage() {
        return String.format("%.2f%%", getChunkProgress());
    }

    /**
     * 获取总进度百分比字符串
     */
    public String getTotalProgressPercentage() {
        return String.format("%.2f%%", getTotalProgress());
    }
}