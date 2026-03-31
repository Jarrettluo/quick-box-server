package com.jiaruiblog.quickboxserver.model.folder;

import com.jiaruiblog.quickboxserver.exception.BusinessException;
import com.jiaruiblog.quickboxserver.exception.ErrorCode;
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
            throw new BusinessException(ErrorCode.UPLOAD_SESSION_ID_EMPTY);
        }

        if (chunkNumber == null || chunkNumber < 1) {
            throw new BusinessException(ErrorCode.CHUNK_NUMBER_INVALID);
        }

        if (totalChunks == null || totalChunks <= 0) {
            throw new BusinessException(ErrorCode.TOTAL_CHUNKS_MUST_POSITIVE);
        }

        if (chunkNumber > totalChunks) {
            throw new BusinessException(ErrorCode.CHUNK_NUMBER_EXCEEDS_TOTAL);
        }

        if (currentChunkSize == null || currentChunkSize <= 0) {
            throw new BusinessException(ErrorCode.CHUNK_SIZE_MUST_POSITIVE);
        }

        if (filename == null || filename.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_NAME_EMPTY);
        }

        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.CHUNK_FILE_EMPTY);
        }

        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.RELATIVE_PATH_EMPTY);
        }
    }

    /**
     * 检查是否是最后一个分片
     */
    public boolean isLastChunk() {
        return chunkNumber != null && totalChunks != null && chunkNumber.equals(totalChunks);
    }
}
