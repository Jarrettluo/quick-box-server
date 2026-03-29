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

    // ==================== 文件夹下载管理 ====================

    /**
     * 下载文件夹为ZIP
     * @param accessCode 取件码
     * @return ZIP文件流
     */
    InputStream downloadFolderAsZip(String accessCode);

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

    // ==================== 工具方法 ====================

    /**
     * 生成取件码
     * @return 取件码
     */
    String generateAccessCode();

    /**
     * 验证文件路径
     * @param relativePath 相对路径
     * @return 是否有效
     */
    boolean validateFilePath(String relativePath);
}