package com.jiaruiblog.quickboxserver.model.request;

public class ChunkUploadRequest {
    // 文件唯一标识（MD5或UUID）
    private String fileId;
    // 当前分片序号（从1开始）
    private Integer chunkNumber;
    // 分片大小（字节）
    private Long chunkSize;
    // 当前分片大小（可能最后一个分片较小）
    private Long currentChunkSize;
    // 总分片数
    private Integer totalChunks;
    // 文件总大小
    private Long totalSize;
    // 文件名
    private String filename;
    // 相对路径
    private String relativePath;
    // 文件类型
    private String contentType;

    // 文件整体MD5
    private String fileMd5;
    // 当前分片的MD5
    private String chunkMd5;
}