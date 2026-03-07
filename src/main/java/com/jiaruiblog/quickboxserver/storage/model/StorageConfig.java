package com.jiaruiblog.quickboxserver.storage.model;

import lombok.Data;

/**
 * 存储配置信息
 */
@Data
public class StorageConfig {
    /**
     * 存储类型
     */
    private StorageType type;

    /**
     * 存储名称（唯一标识）
     */
    private String name;

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 是否为主存储
     */
    private boolean primary = false;

    /**
     * 存储优先级（数值越小优先级越高）
     */
    private int priority = 10;

    /**
     * 最大文件大小（字节）
     */
    private long maxFileSize = 100 * 1024 * 1024; // 100MB

    /**
     * 最大请求大小（字节）
     */
    private long maxRequestSize = 100 * 1024 * 1024; // 100MB

    /**
     * 连接超时时间（毫秒）
     */
    private int connectionTimeout = 30000;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 30000;

    /**
     * 重试次数
     */
    private int retryCount = 3;

    /**
     * 重试间隔（毫秒）
     */
    private int retryInterval = 1000;

    /**
     * 本地文件系统配置
     */
    private LocalConfig localConfig;

    /**
     * S3/MinIO配置
     */
    private S3Config s3Config;

    /**
     * WebDAV配置
     */
    private WebDAVConfig webdavConfig;

    /**
     * NAS配置
     */
    private NASConfig nasConfig;

    /**
     * 健康检查配置
     */
    private HealthCheckConfig healthCheckConfig = new HealthCheckConfig();

    /**
     * 本地文件系统配置
     */
    @Data
    public static class LocalConfig {
        private String basePath;
        private String chunkPath;
        private String filePath;
        private boolean createDirectories = true;
        private boolean useTempFiles = true;
        private String tempFilePrefix = "quickbox_";
    }

    /**
     * S3/MinIO配置
     */
    @Data
    public static class S3Config {
        private String endpoint;
        private String region;
        private String accessKey;
        private String secretKey;
        private String bucketName;
        private boolean pathStyleAccess = true;
        private boolean useSSL = false;
        private String prefix = "";
        private int partSize = 5 * 1024 * 1024; // 5MB
    }

    /**
     * WebDAV配置
     */
    @Data
    public static class WebDAVConfig {
        private String endpoint;
        private String username;
        private String password;
        private String basePath = "/";
        private boolean useSSL = false;
        private boolean validateSSL = true;
    }

    /**
     * NAS配置
     */
    @Data
    public static class NASConfig {
        private String type; // "nfs" or "smb"
        private String server;
        private String sharePath;
        private String mountPoint;
        private String username;
        private String password;
        private String domain;
        private boolean autoMount = true;
    }

    /**
     * 健康检查配置
     */
    @Data
    public static class HealthCheckConfig {
        private boolean enabled = true;
        private int interval = 30000; // 30秒
        private int timeout = 5000; // 5秒
        private int failureThreshold = 3;
        private int successThreshold = 2;
    }
}