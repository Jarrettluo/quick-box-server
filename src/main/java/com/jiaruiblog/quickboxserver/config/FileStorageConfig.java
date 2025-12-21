package com.jiaruiblog.quickboxserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "file.storage")
public class FileStorageConfig {

    /**
     * 基础存储路径
     * Linux默认: /var/uploads
     * Windows默认: C:/uploads
     */
//    private String basePath = "/var/uploads";
    private String basePath = "C:/app";

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
     */
    public String getFullChunksPath() {
        return basePath + "/" + chunksPath;
    }

    /**
     * 获取完整的最终文件路径
     */
    public String getFullFinalPath() {
        return basePath + "/" + finalPath;
    }
}