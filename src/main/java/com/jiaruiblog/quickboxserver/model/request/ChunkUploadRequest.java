package com.jiaruiblog.quickboxserver.model.request;

import org.springframework.web.multipart.MultipartFile;

public record ChunkUploadRequest(
    String identifier,       // 文件唯一标识（对应vue-simple-uploader的identifier）
    Integer chunkNumber,     // 当前分片序号
    Long chunkSize,          // 分片大小
    Long currentChunkSize,   // 当前分片实际大小
    Integer totalChunks,     // 总分片数
    Long totalSize,          // 文件总大小
    String filename,         // 文件名
    String relativePath,     // 相对路径（可选）
    String contentType       // 文件类型
) {
    // 可以添加便捷方法
    public boolean isLastChunk() {
        return chunkNumber != null && totalChunks != null 
               && chunkNumber.equals(totalChunks);
    }
}