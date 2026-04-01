package com.jiaruiblog.quickboxserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 存储配置属性
 * 从 application.yml / application-prod.yml 读取
 *
 * 配置示例：
 *
 * # 本地存储模式
 * storage:
 *   type: local
 *   local:
 *     base-path: /data/storage
 *     chunks-path: chunks
 *     final-path: files
 *
 * # S3 存储模式
 * storage:
 *   type: s3
 *   s3:
 *     endpoint: http://minio:9000
 *     region: us-east-1
 *     access-key: your-access-key
 *     secret-key: your-secret-key
 *     bucket-name: quickbox-files
 *     prefix: uploads
 */
@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /**
     * 存储类型：local 或 s3
     */
    private String type = "local";

    /**
     * 本地存储配置
     */
    private LocalConfig local = new LocalConfig();

    /**
     * S3 存储配置
     */
    private S3Config s3 = new S3Config();

    @Data
    public static class LocalConfig {
        /**
         * 基础存储路径
         */
        private String basePath = "./uploads";

        /**
         * 分片存储目录（相对于 base-path）
         */
        private String chunksPath = "chunks";

        /**
         * 最终文件存储目录（相对于 base-path）
         */
        private String finalPath = "files";

        /**
         * 是否自动创建目录
         */
        private boolean createDirectories = true;

        /**
         * 是否使用临时文件
         */
        private boolean useTempFiles = true;

        /**
         * 临时文件前缀
         */
        private String tempFilePrefix = "quickbox_";
    }

    @Data
    public static class S3Config {
        /**
         * 是否启用 S3 存储
         */
        private boolean enabled = false;

        /**
         * S3 端点地址（支持 MinIO）
         */
        private String endpoint = "http://minio:9000";

        /**
         * AWS 区域
         */
        private String region = "us-east-1";

        /**
         * Access Key
         */
        private String accessKey;

        /**
         * Secret Key
         */
        private String secretKey;

        /**
         * Bucket 名称
         */
        private String bucketName = "quickbox-files";

        /**
         * 是否使用路径样式访问
         */
        private boolean pathStyleAccess = true;

        /**
         * 是否使用 SSL
         */
        private boolean useSsl = false;

        /**
         * 对象键前缀
         */
        private String prefix = "uploads";

        /**
         * 分片大小（字节）
         */
        private int partSize = 5 * 1024 * 1024; // 5MB
    }
}
