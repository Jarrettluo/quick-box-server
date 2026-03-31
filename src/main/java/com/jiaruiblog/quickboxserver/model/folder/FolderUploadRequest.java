package com.jiaruiblog.quickboxserver.model.folder;

import com.jiaruiblog.quickboxserver.exception.BusinessException;
import com.jiaruiblog.quickboxserver.exception.ErrorCode;
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
            throw new BusinessException(ErrorCode.FOLDER_NAME_EMPTY);
        }

        if (totalFiles == null || totalFiles <= 0) {
            throw new BusinessException(ErrorCode.TOTAL_FILES_MUST_POSITIVE);
        }

        if (totalSize == null || totalSize <= 0) {
            throw new BusinessException(ErrorCode.TOTAL_SIZE_MUST_POSITIVE);
        }

        if (expireSeconds != null && expireSeconds < 60) {
            throw new BusinessException(ErrorCode.EXPIRE_TIME_TOO_SHORT);
        }
    }
}
