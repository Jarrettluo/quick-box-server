package com.jiaruiblog.quickboxserver.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Path;

public interface StorageService {

    /**
     * 存储分片文件
     */
    void storeChunk(MultipartFile file, Path location) throws IOException;

    /**
     * 合并分片文件
     */
    Path mergeChunks(Path chunkDir, String targetFilename) throws IOException;

    /**
     * 创建分片目录
     */
    Path createChunkDirectory(String relativePath) throws IOException;

    /**
     * 清理目录
     */
    void cleanupDirectory(Path dir) throws IOException;
}