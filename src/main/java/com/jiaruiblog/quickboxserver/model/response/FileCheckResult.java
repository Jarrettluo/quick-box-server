package com.jiaruiblog.quickboxserver.model.response;

import java.util.List;

public record FileCheckResult(
    boolean exists,
    String fileUrl,
    List<Integer> uploadedChunks,
    String uploadId,
    String chunkPath
) {}