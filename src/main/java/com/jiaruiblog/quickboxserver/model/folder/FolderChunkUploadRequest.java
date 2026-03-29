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
     * 当前分片大小（字节）
     */
    private Long currentChunkSize;

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

        if (currentChunkSize == null || currentChunkSize <= 0) {
            throw new IllegalArgumentException("当前分片大小必须大于0");
        }

        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("分片文件不能为空");
        }

        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("相对路径不能为空");
        }
    }

    /**
     * 检查是否是最后一个分片
     */
    public boolean isLastChunk() {
        return chunkNumber != null && totalChunks != null && chunkNumber.equals(totalChunks);
    }
}
