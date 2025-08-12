package com.jiaruiblog.quickboxserver.model.response;

import java.time.LocalDateTime;

// 上传会话信息
public record UploadSession(
        String uploadId,      // 唯一上传ID
        String chunkPath,     // 分片存储路径 如: "uploads/chunks/{date}/{uploadId}/"
        LocalDateTime expires // 会话过期时间
) {}