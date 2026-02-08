package com.jiaruiblog.quickboxserver.storage.model;

/**
 * 存储类型枚举
 */
public enum StorageType {
    LOCAL("local", "本地文件系统"),
    S3("s3", "S3对象存储"),
    MINIO("minio", "MinIO对象存储"),
    WEBDAV("webdav", "WebDAV存储"),
    NAS("nas", "NAS存储"),
    GPFS("gpfs", "GPFS存储");

    private final String code;
    private final String description;

    StorageType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static StorageType fromCode(String code) {
        for (StorageType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的存储类型: " + code);
    }
}