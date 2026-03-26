package com.jiaruiblog.quickboxserver.service.folder;

import com.jiaruiblog.quickboxserver.model.folder.*;

import java.io.InputStream;

/**
 * 文件夹上传服务接口
 */
public interface FolderUploadService {

    // ==================== 文件夹上传管理 ====================

    /**
     * 初始化文件夹上传
     * @param request 文件夹上传请求
     * @return 文件夹上传响应
     */
    FolderUploadResponse initFolderUpload(FolderUploadRequest request);

    /**
     * 上传文件夹分片
     * @param request 文件夹分片上传请求
     * @return 上传进度响应
     */
    FolderUploadResponse uploadFolderChunk(FolderChunkUploadRequest request);

    /**
     * 合并文件夹分片
     * @param sessionId 上传会话ID
     * @return 合并结果响应
     */
    FolderUploadResponse mergeFolderChunks(String sessionId);

    /**
     * 取消文件夹上传
     * @param sessionId 上传会话ID
     */
    void cancelFolderUpload(String sessionId);

    /**
     * 获取上传进度
     * @param sessionId 上传会话ID
     * @return 上传进度响应
     */
    FolderUploadResponse getUploadProgress(String sessionId);

    // ==================== 文件夹信息管理 ====================

    /**
     * 获取文件夹信息
     * @param accessCode 取件码
     * @return 文件夹信息响应
     */
    FolderInfoResponse getFolderInfo(String accessCode);

    /**
     * 验证取件码
     * @param accessCode 取件码
     * @return 是否有效
     */
    boolean validateAccessCode(String accessCode);

    /**
     * 更新文件夹元数据
     * @param accessCode 取件码
     * @param metadata 元数据
     */
    void updateFolderMetadata(String accessCode, String metadata);

    // ==================== 文件夹下载管理 ====================

    /**
     * 下载文件夹为ZIP
     * @param accessCode 取件码
     * @return ZIP文件流
     */
    InputStream downloadFolderAsZip(String accessCode);

    /**
     * 获取文件夹下载URL
     * @param accessCode 取件码
     * @return 下载URL
     */
    String getFolderDownloadUrl(String accessCode);

    /**
     * 获取文件下载URL
     * @param accessCode 取件码
     * @param relativePath 相对路径
     * @return 下载URL
     */
    String getFileDownloadUrl(String accessCode, String relativePath);

    // ==================== 文件夹清理管理 ====================

    /**
     * 清理过期文件夹
     */
    void cleanupExpiredFolders();

    /**
     * 删除文件夹
     * @param accessCode 取件码
     */
    void deleteFolder(String accessCode);

    /**
     * 批量删除文件夹
     * @param accessCodes 取件码列表
     */
    void batchDeleteFolders(java.util.List<String> accessCodes);

    // ==================== 统计和监控 ====================

    /**
     * 获取文件夹统计信息
     * @return 统计信息
     */
    FolderStats getFolderStats();

    /**
     * 获取上传会话统计
     * @return 会话统计
     */
    UploadSessionStats getUploadSessionStats();

    /**
     * 获取存储使用情况
     * @return 存储使用情况
     */
    StorageUsageStats getStorageUsageStats();

    // ==================== 工具方法 ====================

    /**
     * 生成取件码
     * @return 取件码
     */
    String generateAccessCode();

    /**
     * 验证文件夹名称
     * @param folderName 文件夹名称
     * @return 是否有效
     */
    boolean validateFolderName(String folderName);

    /**
     * 验证文件路径
     * @param relativePath 相对路径
     * @return 是否有效
     */
    boolean validateFilePath(String relativePath);

    /**
     * 计算文件夹哈希值
     * @param accessCode 取件码
     * @return 哈希值
     */
    String calculateFolderHash(String accessCode);

    // ==================== 事件监听 ====================

    /**
     * 添加文件夹事件监听器
     * @param listener 监听器
     */
    void addFolderEventListener(FolderEventListener listener);

    /**
     * 移除文件夹事件监听器
     * @param listener 监听器
     */
    void removeFolderEventListener(FolderEventListener listener);

    // ==================== 内部类和接口 ====================

    /**
     * 文件夹统计信息
     */
    interface FolderStats {
        long getTotalFolders();
        long getTotalFiles();
        long getTotalSize();
        long getActiveUploads();
        long getCompletedUploads();
        long getFailedUploads();
        double getSuccessRate();
    }

    /**
     * 上传会话统计
     */
    interface UploadSessionStats {
        long getTotalSessions();
        long getActiveSessions();
        long getCompletedSessions();
        long getFailedSessions();
        long getCancelledSessions();
        double getAverageUploadTime();
        double getAverageFileSize();
    }

    /**
     * 存储使用情况统计
     */
    interface StorageUsageStats {
        long getTotalSpace();
        long getUsedSpace();
        long getAvailableSpace();
        double getUsedPercentage();
        long getFolderCount();
        long getFileCount();
    }

    /**
     * 文件夹事件监听器
     */
    interface FolderEventListener {
        default void onFolderUploadStarted(FolderUploadResponse response) {}
        default void onFolderChunkUploaded(FolderUploadResponse response, int chunkNumber) {}
        default void onFolderUploadCompleted(FolderUploadResponse response) {}
        default void onFolderUploadFailed(FolderUploadResponse response, String error) {}
        default void onFolderUploadCancelled(FolderUploadResponse response) {}
        default void onFolderDownloadStarted(FolderInfoResponse response) {}
        default void onFolderDownloadCompleted(FolderInfoResponse response) {}
        default void onFolderDownloadFailed(FolderInfoResponse response, String error) {}
        default void onFolderDeleted(FolderInfoResponse response) {}
        default void onFolderExpired(FolderInfoResponse response) {}
    }
}