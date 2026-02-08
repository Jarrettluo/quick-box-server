package com.jiaruiblog.quickboxserver.storage;

import com.jiaruiblog.quickboxserver.storage.model.FileInfo;
import com.jiaruiblog.quickboxserver.storage.model.FolderInfo;

/**
 * 存储事件监听器接口
 */
public interface StorageEventListener {

    // ==================== 文件事件 ====================

    /**
     * 文件上传开始
     * @param fileInfo 文件信息
     */
    default void onFileUploadStarted(FileInfo fileInfo) {}

    /**
     * 文件分片上传完成
     * @param fileInfo 文件信息
     * @param chunkNumber 分片序号
     * @param chunkSize 分片大小
     */
    default void onFileChunkUploaded(FileInfo fileInfo, int chunkNumber, long chunkSize) {}

    /**
     * 文件上传完成
     * @param fileInfo 文件信息
     */
    default void onFileUploadCompleted(FileInfo fileInfo) {}

    /**
     * 文件上传失败
     * @param fileInfo 文件信息
     * @param error 错误信息
     */
    default void onFileUploadFailed(FileInfo fileInfo, String error) {}

    /**
     * 文件下载开始
     * @param fileInfo 文件信息
     */
    default void onFileDownloadStarted(FileInfo fileInfo) {}

    /**
     * 文件下载完成
     * @param fileInfo 文件信息
     * @param bytesDownloaded 下载字节数
     */
    default void onFileDownloadCompleted(FileInfo fileInfo, long bytesDownloaded) {}

    /**
     * 文件下载失败
     * @param fileInfo 文件信息
     * @param error 错误信息
     */
    default void onFileDownloadFailed(FileInfo fileInfo, String error) {}

    /**
     * 文件删除
     * @param fileInfo 文件信息
     */
    default void onFileDeleted(FileInfo fileInfo) {}

    // ==================== 文件夹事件 ====================

    /**
     * 文件夹上传开始
     * @param folderInfo 文件夹信息
     */
    default void onFolderUploadStarted(FolderInfo folderInfo) {}

    /**
     * 文件夹分片上传完成
     * @param folderInfo 文件夹信息
     * @param chunkNumber 分片序号
     * @param chunkSize 分片大小
     */
    default void onFolderChunkUploaded(FolderInfo folderInfo, int chunkNumber, long chunkSize) {}

    /**
     * 文件夹上传完成
     * @param folderInfo 文件夹信息
     */
    default void onFolderUploadCompleted(FolderInfo folderInfo) {}

    /**
     * 文件夹上传失败
     * @param folderInfo 文件夹信息
     * @param error 错误信息
     */
    default void onFolderUploadFailed(FolderInfo folderInfo, String error) {}

    /**
     * 文件夹下载开始
     * @param folderInfo 文件夹信息
     */
    default void onFolderDownloadStarted(FolderInfo folderInfo) {}

    /**
     * 文件夹下载完成
     * @param folderInfo 文件夹信息
     * @param bytesDownloaded 下载字节数
     */
    default void onFolderDownloadCompleted(FolderInfo folderInfo, long bytesDownloaded) {}

    /**
     * 文件夹下载失败
     * @param folderInfo 文件夹信息
     * @param error 错误信息
     */
    default void onFolderDownloadFailed(FolderInfo folderInfo, String error) {}

    /**
     * 文件夹删除
     * @param folderInfo 文件夹信息
     */
    default void onFolderDeleted(FolderInfo folderInfo) {}

    // ==================== 存储事件 ====================

    /**
     * 存储服务连接成功
     * @param storageName 存储名称
     */
    default void onStorageConnected(String storageName) {}

    /**
     * 存储服务连接失败
     * @param storageName 存储名称
     * @param error 错误信息
     */
    default void onStorageConnectionFailed(String storageName, String error) {}

    /**
     * 存储服务断开连接
     * @param storageName 存储名称
     */
    default void onStorageDisconnected(String storageName) {}

    /**
     * 存储服务状态变化
     * @param storageName 存储名称
     * @param oldStatus 旧状态
     * @param newStatus 新状态
     */
    default void onStorageStatusChanged(String storageName, String oldStatus, String newStatus) {}

    /**
     * 存储空间不足警告
     * @param storageName 存储名称
     * @param availableSpace 可用空间
     * @param totalSpace 总空间
     * @param usedPercentage 使用百分比
     */
    default void onStorageSpaceLow(String storageName, long availableSpace, long totalSpace, double usedPercentage) {}

    /**
     * 存储空间严重不足
     * @param storageName 存储名称
     * @param availableSpace 可用空间
     * @param totalSpace 总空间
     * @param usedPercentage 使用百分比
     */
    default void onStorageSpaceCritical(String storageName, long availableSpace, long totalSpace, double usedPercentage) {}

    // ==================== 错误事件 ====================

    /**
     * 存储操作错误
     * @param operation 操作类型
     * @param storageName 存储名称
     * @param error 错误信息
     */
    default void onStorageError(String operation, String storageName, String error) {}

    /**
     * 存储操作超时
     * @param operation 操作类型
     * @param storageName 存储名称
     * @param timeout 超时时间（毫秒）
     */
    default void onStorageTimeout(String operation, String storageName, long timeout) {}

    /**
     * 存储操作重试
     * @param operation 操作类型
     * @param storageName 存储名称
     * @param retryCount 重试次数
     * @param maxRetries 最大重试次数
     */
    default void onStorageRetry(String operation, String storageName, int retryCount, int maxRetries) {}

    // ==================== 性能事件 ====================

    /**
     * 存储操作性能指标
     * @param operation 操作类型
     * @param storageName 存储名称
     * @param duration 持续时间（毫秒）
     * @param bytesTransferred 传输字节数
     */
    default void onStoragePerformance(String operation, String storageName, long duration, long bytesTransferred) {}

    /**
     * 存储操作吞吐量
     * @param operation 操作类型
     * @param storageName 存储名称
     * @param throughput 吞吐量（字节/秒）
     */
    default void onStorageThroughput(String operation, String storageName, double throughput) {}

    // ==================== 批量操作事件 ====================

    /**
     * 批量操作开始
     * @param operation 操作类型
     * @param storageName 存储名称
     * @param totalItems 总项目数
     */
    default void onBatchOperationStarted(String operation, String storageName, int totalItems) {}

    /**
     * 批量操作进度
     * @param operation 操作类型
     * @param storageName 存储名称
     * @param processedItems 已处理项目数
     * @param totalItems 总项目数
     * @param progressPercentage 进度百分比
     */
    default void onBatchOperationProgress(String operation, String storageName, int processedItems, int totalItems, double progressPercentage) {}

    /**
     * 批量操作完成
     * @param operation 操作类型
     * @param storageName 存储名称
     * @param successCount 成功数
     * @param failureCount 失败数
     * @param totalItems 总项目数
     */
    default void onBatchOperationCompleted(String operation, String storageName, int successCount, int failureCount, int totalItems) {}

    // ==================== 清理事件 ====================

    /**
     * 清理操作开始
     * @param storageName 存储名称
     * @param cleanupType 清理类型
     */
    default void onCleanupStarted(String storageName, String cleanupType) {}

    /**
     * 清理操作进度
     * @param storageName 存储名称
     * @param cleanupType 清理类型
     * @param cleanedItems 已清理项目数
     * @param totalItems 总项目数
     */
    default void onCleanupProgress(String storageName, String cleanupType, int cleanedItems, int totalItems) {}

    /**
     * 清理操作完成
     * @param storageName 存储名称
     * @param cleanupType 清理类型
     * @param cleanedItems 已清理项目数
     * @param freedSpace 释放空间（字节）
     */
    default void onCleanupCompleted(String storageName, String cleanupType, int cleanedItems, long freedSpace) {}

    // ==================== 自定义事件 ====================

    /**
     * 自定义存储事件
     * @param eventType 事件类型
     * @param storageName 存储名称
     * @param data 事件数据
     */
    default void onCustomStorageEvent(String eventType, String storageName, Object data) {}
}