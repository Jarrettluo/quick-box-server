package com.jiaruiblog.quickboxserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Data
@Configuration
@ConfigurationProperties(prefix = "file.storage")
public class FileStorageConfig {

    /**
     * 基础存储路径
     * Linux默认: /var/uploads
     * Windows默认: ./uploads（相对路径，相对于应用程序运行目录）
     */
    private String basePath = "./uploads";

    /**
     * 分片存储路径，相对于basePath
     */
    private String chunksPath = "chunks";

    /**
     * 最终文件存储路径，相对于basePath
     */
    private String finalPath = "files";

    /**
     * 上传会话过期时间（小时）
     */
    private int sessionExpirationHours = 24;

    /**
     * 下载链接过期时间（天）
     */
    private int downloadExpirationDays = 7;

    /**
     * 获取完整的分片路径
     * 使用 Paths.get() 确保路径分隔符兼容性
     * 并返回绝对路径以避免相对路径解析错误
     */
    public String getFullChunksPath() {
        return Paths.get(basePath, chunksPath).toAbsolutePath().toString();
    }

    /**
     * 获取完整的最终文件路径
     * 使用 Paths.get() 确保路径分隔符兼容性
     * 并返回绝对路径以避免相对路径解析错误
     */
    public String getFullFinalPath() {
        return Paths.get(basePath, finalPath).toAbsolutePath().toString();
    }

    /**
     * 获取分片存储的完整路径（带访问码）
     */
    public String getChunkPathWithAccessCode(String accessCode) {
        return Paths.get(basePath, chunksPath, accessCode).toAbsolutePath().toString();
    }

    /**
     * 获取最终文件存储的完整路径（带访问码）
     */
    public String getFinalPathWithAccessCode(String accessCode) {
        return Paths.get(basePath, finalPath, accessCode).toAbsolutePath().toString();
    }

    /**
     * 获取标准化的路径对象
     */
    public Path getFullChunksPathAsPath() {
        return Paths.get(basePath, chunksPath).toAbsolutePath();
    }

    /**
     * 获取标准化的最终文件路径对象
     */
    public Path getFullFinalPathAsPath() {
        return Paths.get(basePath, finalPath).toAbsolutePath();
    }
}