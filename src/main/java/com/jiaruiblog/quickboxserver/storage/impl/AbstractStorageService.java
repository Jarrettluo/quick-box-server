package com.jiaruiblog.quickboxserver.storage.impl;

import com.jiaruiblog.quickboxserver.storage.StorageEventListener;
import com.jiaruiblog.quickboxserver.storage.StorageService;
import com.jiaruiblog.quickboxserver.storage.model.*;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 抽象存储服务基类
 * 提供通用的实现和事件处理机制
 */
@Slf4j
public abstract class AbstractStorageService implements StorageService {

    protected final StorageConfig config;
    protected final StorageType storageType;
    protected final String storageName;

    protected StorageHealth healthStatus = new StorageHealth();
    protected StorageStats stats = new StorageStats();
    protected StorageUsage usage = new StorageUsage();

    private final List<StorageEventListener> listeners = new CopyOnWriteArrayList<>();

    protected AbstractStorageService(StorageConfig config) {
        this.config = config;
        this.storageType = config.getType();
        this.storageName = config.getName();

        // 初始化健康状态
        healthStatus.setStorageName(storageName);
        healthStatus.setStorageType(storageType);
        healthStatus.setStatus(StorageHealth.HealthStatus.UNKNOWN);
        healthStatus.setLastCheckTime(LocalDateTime.now());

        // 初始化统计信息
        stats.setStorageName(storageName);
        stats.setStorageType(storageType);
        stats.setStartTime(LocalDateTime.now());

        // 初始化使用情况
        usage.setStorageName(storageName);
        usage.setStorageType(storageType);
    }

    // ==================== 基本信息实现 ====================

    @Override
    public StorageType getStorageType() {
        return storageType;
    }

    @Override
    public String getStorageName() {
        return storageName;
    }

    @Override
    public StorageConfig getConfig() {
        return config;
    }

    @Override
    public boolean isAvailable() {
        updateHealthStatus();
        return healthStatus.isAvailable();
    }

    // ==================== 事件监听器管理 ====================

    @Override
    public void addStorageEventListener(StorageEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            log.debug("添加存储事件监听器: {}", listener.getClass().getSimpleName());
        }
    }

    @Override
    public void removeStorageEventListener(StorageEventListener listener) {
        listeners.remove(listener);
        log.debug("移除存储事件监听器: {}", listener.getClass().getSimpleName());
    }

    // ==================== 事件触发方法 ====================

    protected void fireFileUploadStarted(FileInfo fileInfo) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFileUploadStarted(fileInfo);
            } catch (Exception e) {
                log.error("触发文件上传开始事件失败", e);
            }
        }
    }

    protected void fireFileChunkUploaded(FileInfo fileInfo, int chunkNumber, long chunkSize) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFileChunkUploaded(fileInfo, chunkNumber, chunkSize);
            } catch (Exception e) {
                log.error("触发文件分片上传完成事件失败", e);
            }
        }
    }

    protected void fireFileUploadCompleted(FileInfo fileInfo) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFileUploadCompleted(fileInfo);
            } catch (Exception e) {
                log.error("触发文件上传完成事件失败", e);
            }
        }
    }

    protected void fireFileUploadFailed(FileInfo fileInfo, String error) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFileUploadFailed(fileInfo, error);
            } catch (Exception e) {
                log.error("触发文件上传失败事件失败", e);
            }
        }
    }

    protected void fireFileDownloadStarted(FileInfo fileInfo) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFileDownloadStarted(fileInfo);
            } catch (Exception e) {
                log.error("触发文件下载开始事件失败", e);
            }
        }
    }

    protected void fireFileDownloadCompleted(FileInfo fileInfo, long bytesDownloaded) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFileDownloadCompleted(fileInfo, bytesDownloaded);
            } catch (Exception e) {
                log.error("触发文件下载完成事件失败", e);
            }
        }
    }

    protected void fireFileDownloadFailed(FileInfo fileInfo, String error) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFileDownloadFailed(fileInfo, error);
            } catch (Exception e) {
                log.error("触发文件下载失败事件失败", e);
            }
        }
    }

    protected void fireFileDeleted(FileInfo fileInfo) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFileDeleted(fileInfo);
            } catch (Exception e) {
                log.error("触发文件删除事件失败", e);
            }
        }
    }

    protected void fireFolderUploadStarted(FolderInfo folderInfo) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFolderUploadStarted(folderInfo);
            } catch (Exception e) {
                log.error("触发文件夹上传开始事件失败", e);
            }
        }
    }

    protected void fireFolderChunkUploaded(FolderInfo folderInfo, int chunkNumber, long chunkSize) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFolderChunkUploaded(folderInfo, chunkNumber, chunkSize);
            } catch (Exception e) {
                log.error("触发文件夹分片上传完成事件失败", e);
            }
        }
    }

    protected void fireFolderUploadCompleted(FolderInfo folderInfo) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFolderUploadCompleted(folderInfo);
            } catch (Exception e) {
                log.error("触发文件夹上传完成事件失败", e);
            }
        }
    }

    protected void fireFolderUploadFailed(FolderInfo folderInfo, String error) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onFolderUploadFailed(folderInfo, error);
            } catch (Exception e) {
                log.error("触发文件夹上传失败事件失败", e);
            }
        }
    }

    protected void fireStorageError(String operation, String error) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onStorageError(operation, storageName, error);
            } catch (Exception e) {
                log.error("触发存储错误事件失败", e);
            }
        }
    }

    protected void fireStoragePerformance(String operation, long duration, long bytesTransferred) {
        for (StorageEventListener listener : listeners) {
            try {
                listener.onStoragePerformance(operation, storageName, duration, bytesTransferred);
            } catch (Exception e) {
                log.error("触发存储性能事件失败", e);
            }
        }
    }

    // ==================== 健康状态管理 ====================

    /**
     * 更新健康状态（子类应覆盖此方法）
     */
    protected abstract void updateHealthStatus();

    @Override
    public StorageHealth getHealthStatus() {
        updateHealthStatus();
        return healthStatus;
    }

    // ==================== 统计信息管理 ====================

    @Override
    public StorageStats getStorageStats() {
        stats.setEndTime(LocalDateTime.now());
        return stats;
    }

    @Override
    public void resetStats() {
        stats = new StorageStats();
        stats.setStorageName(storageName);
        stats.setStorageType(storageType);
        stats.setStartTime(LocalDateTime.now());
        log.info("重置存储统计信息: {}", storageName);
    }

    // ==================== 使用情况管理 ====================

    @Override
    public StorageUsage getStorageUsage() {
        updateStorageUsage();
        return usage;
    }

    /**
     * 更新存储使用情况（子类应覆盖此方法）
     */
    protected abstract void updateStorageUsage();

    // ==================== 默认实现（抛出UnsupportedOperationException） ====================

    @Override
    public String initFolderUpload(String folderId, String folderName, int totalFiles, long totalSize, String structureJson) {
        throw new UnsupportedOperationException("文件夹上传不支持");
    }

    @Override
    public void uploadFolderChunk(String sessionId, int chunkNumber, InputStream chunkData, long chunkSize) {
        throw new UnsupportedOperationException("文件夹分片上传不支持");
    }

    @Override
    public String mergeFolderChunks(String sessionId) {
        throw new UnsupportedOperationException("文件夹分片合并不支持");
    }

    @Override
    public InputStream downloadFolderAsZip(String folderPath) {
        throw new UnsupportedOperationException("文件夹ZIP下载不支持");
    }

    @Override
    public InputStream downloadFolderFile(String folderPath, String relativePath) {
        throw new UnsupportedOperationException("文件夹文件下载不支持");
    }

    @Override
    public void deleteFolder(String folderPath) {
        throw new UnsupportedOperationException("文件夹删除不支持");
    }

    @Override
    public FolderInfo getFolderInfo(String folderPath) {
        throw new UnsupportedOperationException("文件夹信息获取不支持");
    }

    @Override
    public boolean folderExists(String folderPath) {
        throw new UnsupportedOperationException("文件夹存在检查不支持");
    }

    @Override
    public void downloadFileToStream(String filePath, OutputStream outputStream) {
        throw new UnsupportedOperationException("文件流式下载不支持");
    }

    @Override
    public List<StorageItem> listDirectory(String path) {
        throw new UnsupportedOperationException("目录列表不支持");
    }

    @Override
    public void createDirectory(String path) {
        throw new UnsupportedOperationException("目录创建不支持");
    }

    @Override
    public void deleteDirectory(String path) {
        throw new UnsupportedOperationException("目录删除不支持");
    }

    @Override
    public void batchUploadFiles(Map<String, InputStream> files) {
        throw new UnsupportedOperationException("批量上传不支持");
    }

    @Override
    public Map<String, InputStream> batchDownloadFiles(List<String> filePaths) {
        throw new UnsupportedOperationException("批量下载不支持");
    }

    @Override
    public void batchDeleteFiles(List<String> filePaths) {
        throw new UnsupportedOperationException("批量删除不支持");
    }

    // ==================== 工具方法 ====================

    /**
     * 记录操作开始时间
     */
    protected long startOperation() {
        return System.currentTimeMillis();
    }

    /**
     * 记录操作结束时间并计算持续时间
     */
    protected long endOperation(long startTime) {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 更新统计信息
     */
    protected void updateStats(String operation, boolean success, long duration, long bytesTransferred) {
        if (success) {
            stats.setSuccessCount(stats.getSuccessCount() + 1);
        } else {
            stats.setFailureCount(stats.getFailureCount() + 1);
        }

        // 根据操作类型更新特定统计
        switch (operation) {
            case "upload":
                stats.setUploadCount(stats.getUploadCount() + 1);
                stats.setNetworkTransferred(stats.getNetworkTransferred() + bytesTransferred);
                break;
            case "download":
                stats.setDownloadCount(stats.getDownloadCount() + 1);
                stats.setNetworkTransferred(stats.getNetworkTransferred() + bytesTransferred);
                break;
            case "delete":
                stats.setDeleteCount(stats.getDeleteCount() + 1);
                break;
        }

        // 触发性能事件
        fireStoragePerformance(operation, duration, bytesTransferred);
    }

    /**
     * 验证文件路径安全性
     */
    protected void validateFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }

        // 防止目录遍历攻击
        if (filePath.contains("..") || filePath.contains("//") || filePath.contains("\\\\")) {
            throw new SecurityException("文件路径包含非法字符: " + filePath);
        }

        // 检查路径长度
        if (filePath.length() > 4096) {
            throw new IllegalArgumentException("文件路径过长: " + filePath);
        }
    }

    /**
     * 验证文件夹路径安全性
     */
    protected void validateFolderPath(String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new IllegalArgumentException("文件夹路径不能为空");
        }

        // 防止目录遍历攻击
        if (folderPath.contains("..") || folderPath.contains("//") || folderPath.contains("\\\\")) {
            throw new SecurityException("文件夹路径包含非法字符: " + folderPath);
        }

        // 检查路径长度
        if (folderPath.length() > 4096) {
            throw new IllegalArgumentException("文件夹路径过长: " + folderPath);
        }
    }

    /**
     * 获取文件扩展名
     */
    protected String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 获取MIME类型
     */
    protected String getMimeType(String fileName) {
        String extension = getFileExtension(fileName);
        switch (extension) {
            case "txt": return "text/plain";
            case "pdf": return "application/pdf";
            case "doc": return "application/msword";
            case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls": return "application/vnd.ms-excel";
            case "xlsx": return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt": return "application/vnd.ms-powerpoint";
            case "pptx": return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "zip": return "application/zip";
            case "mp4": return "video/mp4";
            case "mp3": return "audio/mpeg";
            default: return "application/octet-stream";
        }
    }
}