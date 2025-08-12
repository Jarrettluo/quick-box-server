package com.jiaruiblog.quickboxserver.service;

import com.jiaruiblog.quickboxserver.model.dto.FileInfo;
import com.jiaruiblog.quickboxserver.model.request.ChunkUploadRequest;
import com.jiaruiblog.quickboxserver.model.response.FileCheckResult;
import com.jiaruiblog.quickboxserver.model.response.UploadProgress;
import com.jiaruiblog.quickboxserver.model.response.UploadSession;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;

public interface FileUploadService {

    /**
     * 文件上传前检查（包含MD5去重检查）
     * @param fileMd5 文件MD5值
     * @param filename 原始文件名
     * @param fileSize 文件大小(字节)
     * @return 检查结果
     */
    FileCheckResult prepareUpload(String fileMd5, String filename, long fileSize);

    /**
     * 初始化分片上传会话
     * @param fileMd5 文件MD5值
     * @param filename 原始文件名
     * @return 上传会话信息
     */
    UploadSession initUploadSession(String fileMd5, String filename);

    /**
     * 获取上传进度
     * @param uploadId 上传会话ID
     * @param chunkNumber 指定查询的分片序号(可选)
     * @return 上传进度信息
     */
    UploadProgress getUploadProgress(String uploadId, Integer chunkNumber);

    /**
     * 上传文件分片
     * @param request 分片上传请求
     * @param file 分片文件数据
     * @return 更新后的上传进度
     */
    UploadProgress uploadChunk(ChunkUploadRequest request, MultipartFile file);

    /**
     * 合并所有分片
     * @param fileId 文件唯一标识(可以是uploadId或fileMd5)
     * @return 合并后的文件访问URL
     */
    String mergeChunks(String fileId);

    /**
     * 清理过期上传会话
     * @param before 清理指定时间之前的会话
     */
    void cleanupExpiredSessions(LocalDateTime before);

    File getFileByAccessCode(String accessCode);

    FileInfo getFileInfo(String accessCode);
}