package com.jiaruiblog.quickboxserver.model.response;

import java.time.LocalDateTime;

// 上传会话信息
public record UploadSession(
        // 唯一上传ID， 使用accessCode
        String uploadId,
        // 分片存储路径 如: "uploads/chunks/{uploadId}/"
        String chunkPath,
        // 会话过期时间
        LocalDateTime expires
) {}