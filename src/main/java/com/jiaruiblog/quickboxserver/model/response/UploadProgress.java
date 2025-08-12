package com.jiaruiblog.quickboxserver.model.response;

import java.time.Instant;
import java.util.List;

// 增强后的上传进度
public record UploadProgress(
        String uploadId,
        Integer uploadedChunks,
        Integer totalChunks,
        Boolean completed,
        String fileUrl,
        List<Integer> uploadedChunkNumbers,
        String chunkPath,     // 分片存储路径
        Instant lastModified // 最后上传时间
) {}