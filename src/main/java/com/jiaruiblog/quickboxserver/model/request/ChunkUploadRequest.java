package com.jiaruiblog.quickboxserver.model.request;


public record ChunkUploadRequest(
        // 文件唯一标识（对应vue-simple-uploader的identifier）
        String uploadId,
        // 当前分片序号
        Integer chunkNumber,
        // 分片大小, 123Byte
        Long chunkSize,
        // 当前分片实际大小
        Long currentChunkSize,
        // 总分片数, 100片
        Integer totalChunks,
        // 文件总大小：12300byte
        Long totalSize,
        // 文件名
        String filename,
        // 文件类型
        String contentType
) {
    // 可以添加便捷方法
    public boolean isLastChunk() {
        return chunkNumber != null && totalChunks != null
                && chunkNumber.equals(totalChunks);
    }
}