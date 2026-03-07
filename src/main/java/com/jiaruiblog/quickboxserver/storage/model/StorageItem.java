package com.jiaruiblog.quickboxserver.storage.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 存储项目（文件或目录）
 */
@Data
public class StorageItem {
    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目路径
     */
    private String path;

    /**
     * 项目类型
     */
    private ItemType type;

    /**
     * 文件大小（字节，仅文件有效）
     */
    private Long size;

    /**
     * MIME类型（仅文件有效）
     */
    private String mimeType;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 修改时间
     */
    private LocalDateTime modifyTime;

    /**
     * 访问时间
     */
    private LocalDateTime accessTime;

    /**
     * 权限信息
     */
    private String permissions;

    /**
     * 所有者
     */
    private String owner;

    /**
     * 组
     */
    private String group;

    /**
     * 是否符号链接
     */
    private Boolean symbolicLink = false;

    /**
     * 符号链接目标
     */
    private String linkTarget;

    /**
     * 是否隐藏文件
     */
    private Boolean hidden = false;

    /**
     * 是否可读
     */
    private Boolean readable = true;

    /**
     * 是否可写
     */
    private Boolean writable = true;

    /**
     * 是否可执行
     */
    private Boolean executable = false;

    /**
     * 扩展属性
     */
    private String extendedAttributes;

    /**
     * 项目类型枚举
     */
    public enum ItemType {
        FILE("文件"),
        DIRECTORY("目录"),
        SYMLINK("符号链接"),
        SPECIAL("特殊文件");

        private final String description;

        ItemType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public boolean isFile() {
            return this == FILE;
        }

        public boolean isDirectory() {
            return this == DIRECTORY;
        }

        public boolean isSymlink() {
            return this == SYMLINK;
        }
    }

    /**
     * 获取文件扩展名
     */
    public String getFileExtension() {
        if (name == null || type != ItemType.FILE) {
            return "";
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 获取文件名（不含扩展名）
     */
    public String getFileNameWithoutExtension() {
        if (name == null || type != ItemType.FILE) {
            return name;
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            return name.substring(0, lastDot);
        }
        return name;
    }

    /**
     * 获取文件大小格式化字符串
     */
    public String getFormattedSize() {
        if (size == null) {
            return type == ItemType.DIRECTORY ? "-" : "0 B";
        }

        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 获取图标类型（用于前端显示）
     */
    public String getIconType() {
        if (type == ItemType.DIRECTORY) {
            return "folder";
        } else if (type == ItemType.SYMLINK) {
            return "link";
        }

        // 根据文件扩展名返回图标类型
        String ext = getFileExtension();
        if (ext.isEmpty()) {
            return "file";
        }

        switch (ext) {
            case "pdf":
                return "pdf";
            case "doc":
            case "docx":
                return "word";
            case "xls":
            case "xlsx":
                return "excel";
            case "ppt":
            case "pptx":
                return "powerpoint";
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
            case "svg":
                return "image";
            case "mp4":
            case "avi":
            case "mov":
            case "wmv":
            case "flv":
                return "video";
            case "mp3":
            case "wav":
            case "flac":
            case "aac":
                return "audio";
            case "zip":
            case "rar":
            case "7z":
            case "tar":
            case "gz":
                return "archive";
            case "txt":
            case "md":
            case "log":
                return "text";
            case "html":
            case "htm":
            case "xml":
            case "json":
            case "yaml":
            case "yml":
                return "code";
            default:
                return "file";
        }
    }

    /**
     * 获取人类可读的类型描述
     */
    public String getTypeDescription() {
        if (type == ItemType.DIRECTORY) {
            return "文件夹";
        } else if (type == ItemType.SYMLINK) {
            return "符号链接";
        } else if (type == ItemType.SPECIAL) {
            return "特殊文件";
        }

        // 根据文件扩展名返回描述
        String ext = getFileExtension();
        if (ext.isEmpty()) {
            return "文件";
        }

        switch (ext) {
            case "pdf":
                return "PDF文档";
            case "doc":
            case "docx":
                return "Word文档";
            case "xls":
            case "xlsx":
                return "Excel文档";
            case "ppt":
            case "pptx":
                return "PowerPoint文档";
            case "jpg":
            case "jpeg":
                return "JPEG图片";
            case "png":
                return "PNG图片";
            case "gif":
                return "GIF图片";
            case "mp4":
                return "MP4视频";
            case "mp3":
                return "MP3音频";
            case "zip":
                return "ZIP压缩包";
            case "txt":
                return "文本文件";
            case "html":
            case "htm":
                return "HTML文件";
            default:
                return ext.toUpperCase() + "文件";
        }
    }

    /**
     * 检查是否为图片文件
     */
    public boolean isImage() {
        if (type != ItemType.FILE) {
            return false;
        }
        String ext = getFileExtension();
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") ||
               ext.equals("gif") || ext.equals("bmp") || ext.equals("svg");
    }

    /**
     * 检查是否为文档文件
     */
    public boolean isDocument() {
        if (type != ItemType.FILE) {
            return false;
        }
        String ext = getFileExtension();
        return ext.equals("pdf") || ext.equals("doc") || ext.equals("docx") ||
               ext.equals("xls") || ext.equals("xlsx") || ext.equals("ppt") ||
               ext.equals("pptx") || ext.equals("txt") || ext.equals("md");
    }

    /**
     * 检查是否为媒体文件
     */
    public boolean isMedia() {
        if (type != ItemType.FILE) {
            return false;
        }
        String ext = getFileExtension();
        return ext.equals("mp4") || ext.equals("avi") || ext.equals("mov") ||
               ext.equals("mp3") || ext.equals("wav") || ext.equals("flac");
    }

    /**
     * 检查是否为压缩文件
     */
    public boolean isArchive() {
        if (type != ItemType.FILE) {
            return false;
        }
        String ext = getFileExtension();
        return ext.equals("zip") || ext.equals("rar") || ext.equals("7z") ||
               ext.equals("tar") || ext.equals("gz");
    }
}