package com.jiaruiblog.quickboxserver.storage;

import com.jiaruiblog.quickboxserver.storage.model.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * 存储服务接口
 * 定义所有存储后端必须实现的操作
 */
public interface StorageService {

    // ==================== 基本信息 ====================

    /**
     * 获取存储类型
     */
    StorageType getStorageType();

    /**
     * 获取存储名称
     */
    String getStorageName();

    /**
     * 获取存储配置
     */
    StorageConfig getConfig();

    /**
     * 检查存储服务是否可用
     */
    boolean isAvailable();

    // ==================== 文件操作 ====================

    /**
     * 初始化文件上传
     * @param fileId 文件ID
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param metadata 元数据
     * @return 上传会话ID
     */
    String initFileUpload(String fileId, String fileName, long fileSize, Map<String, Object> metadata);

    /**
     * 上传文件分片
     * @param sessionId 上传会话ID
     * @param chunkNumber 分片序号
     * @param chunkData 分片数据流
     * @param chunkSize 分片大小
     */
    void uploadFileChunk(String sessionId, int chunkNumber, InputStream chunkData, long chunkSize);

    /**
     * 合并文件分片
     * @param sessionId 上传会话ID
     * @return 最终文件路径
     */
    String mergeFileChunks(String sessionId);

    /**
     * 下载文件
     * @param filePath 文件路径
     * @return 文件数据流
     */
    InputStream downloadFile(String filePath);

    /**
     * 下载文件到输出流
     * @param filePath 文件路径
     * @param outputStream 输出流
     */
    void downloadFileToStream(String filePath, OutputStream outputStream);

    /**
     * 删除文件
     * @param filePath 文件路径
     */
    void deleteFile(String filePath);

    /**
     * 获取文件信息
     * @param filePath 文件路径
     * @return 文件信息
     */
    FileInfo getFileInfo(String filePath);

    /**
     * 检查文件是否存在
     * @param filePath 文件路径
     */
    boolean fileExists(String filePath);

    // ==================== 文件夹操作 ====================

    /**
     * 初始化文件夹上传
     * @param folderId 文件夹ID
     * @param folderName 文件夹名
     * @param totalFiles 总文件数
     * @param totalSize 总大小
     * @param structureJson 目录结构JSON
     * @return 上传会话ID
     */
    String initFolderUpload(String folderId, String folderName, int totalFiles, long totalSize, String structureJson);

    /**
     * 上传文件夹分片（ZIP格式）
     * @param sessionId 上传会话ID
     * @param chunkNumber 分片序号
     * @param chunkData 分片数据流
     * @param chunkSize 分片大小
     */
    void uploadFolderChunk(String sessionId, int chunkNumber, InputStream chunkData, long chunkSize);

    /**
     * 合并文件夹分片并解压
     * @param sessionId 上传会话ID
     * @return 文件夹路径
     */
    String mergeFolderChunks(String sessionId);

    /**
     * 下载文件夹为ZIP
     * @param folderPath 文件夹路径
     * @return ZIP数据流
     */
    InputStream downloadFolderAsZip(String folderPath);

    /**
     * 下载文件夹中的单个文件
     * @param folderPath 文件夹路径
     * @param relativePath 相对路径
     * @return 文件数据流
     */
    InputStream downloadFolderFile(String folderPath, String relativePath);

    /**
     * 删除文件夹
     * @param folderPath 文件夹路径
     */
    void deleteFolder(String folderPath);

    /**
     * 获取文件夹信息
     * @param folderPath 文件夹路径
     * @return 文件夹信息
     */
    FolderInfo getFolderInfo(String folderPath);

    /**
     * 检查文件夹是否存在
     * @param folderPath 文件夹路径
     */
    boolean folderExists(String folderPath);

    // ==================== 管理操作 ====================

    /**
     * 清理过期会话
     * @param before 过期时间之前
     */
    void cleanupExpiredSessions(java.time.LocalDateTime before);

    /**
     * 获取存储健康状态
     */
    StorageHealth getHealthStatus();

    /**
     * 获取存储统计信息
     */
    StorageStats getStorageStats();

    /**
     * 重置统计信息
     */
    void resetStats();

    /**
     * 获取存储使用情况
     */
    StorageUsage getStorageUsage();

    /**
     * 列出目录内容
     * @param path 目录路径
     * @return 目录内容列表
     */
    List<StorageItem> listDirectory(String path);

    /**
     * 创建目录
     * @param path 目录路径
     */
    void createDirectory(String path);

    /**
     * 删除目录
     * @param path 目录路径
     */
    void deleteDirectory(String path);

    // ==================== 批量操作 ====================

    /**
     * 批量上传文件
     * @param files 文件映射（路径 -> 数据流）
     */
    void batchUploadFiles(Map<String, InputStream> files);

    /**
     * 批量下载文件
     * @param filePaths 文件路径列表
     * @return 文件映射（路径 -> 数据流）
     */
    Map<String, InputStream> batchDownloadFiles(List<String> filePaths);

    /**
     * 批量删除文件
     * @param filePaths 文件路径列表
     */
    void batchDeleteFiles(List<String> filePaths);

    // ==================== 事件监听 ====================

    /**
     * 添加存储事件监听器
     * @param listener 监听器
     */
    void addStorageEventListener(StorageEventListener listener);

    /**
     * 移除存储事件监听器
     * @param listener 监听器
     */
    void removeStorageEventListener(StorageEventListener listener);
}