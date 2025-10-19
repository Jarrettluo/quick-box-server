package com.jiaruiblog.quickboxserver.model.response;

import java.time.Instant;
import java.util.List;

// 增强后的上传进度
public record UploadProgress(
        // 分片上传的唯一ID，这里是accessCode
        String uploadId,
        // 已经上传的分片数量
        Integer uploadedChunks,
        // 总分片数量
        Integer totalChunks,
        // 已经完成的数量
        Boolean completed,
        // 已经上传的分片信息
        List<Integer> uploadedChunkNumbers,
        // 分片存储路径
        String chunkPath,
        // 最后上传时间
        Instant lastModified
) {}