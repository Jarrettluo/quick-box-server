package com.jiaruiblog.quickboxserver.controller;

import com.jiaruiblog.quickboxserver.common.ApiResult;
import com.jiaruiblog.quickboxserver.model.request.ChunkUploadRequest;
import com.jiaruiblog.quickboxserver.model.request.FileCheckRequest;
import com.jiaruiblog.quickboxserver.model.response.FileCheckResult;
import com.jiaruiblog.quickboxserver.model.response.UploadProgress;
import com.jiaruiblog.quickboxserver.model.response.UploadSession;
import com.jiaruiblog.quickboxserver.service.FileUploadService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    @Resource
    FileUploadService uploadService;

    /**
     * 文件上传前检查（包含去重检查）
     */
    @PostMapping("/prepare")
    public ApiResult<FileCheckResult> prepareUpload(@RequestBody FileCheckRequest request) {
        FileCheckResult result = uploadService.prepareUpload(
                request.fileMd5(),
                request.filename(),
                request.fileSize()
        );
        return ApiResult.success(result);
    }

    /**
     * 初始化分片上传（创建分片存储路径）
     */
    @PostMapping("/init")
    public ApiResult<UploadSession> initChunkUpload(
            @RequestParam String fileMd5,
            @RequestParam String filename) {
        UploadSession session = uploadService.initUploadSession(fileMd5, filename);
        return ApiResult.success(session);
    }

    // 原有检查接口增强（返回分片存储路径）
    @GetMapping("/check")
    public ApiResult<UploadProgress> checkChunk(
            @RequestParam String uploadId, // 改为使用uploadId
            @RequestParam(required = false) Integer chunkNumber) {
        UploadProgress progress = uploadService.getUploadProgress(uploadId, chunkNumber);
        return ApiResult.success(progress);
    }

    /**
     * 上传文件分片
     * @param request 分片上传请求信息
     * @param file 分片文件
     * @return 上传进度信息
     */
    @PostMapping("/chunk")
    public ApiResult<UploadProgress> uploadChunk(
            @ModelAttribute ChunkUploadRequest request,
            @RequestParam("file") MultipartFile file) {
        UploadProgress progress = uploadService.uploadChunk(request, file);
        return ApiResult.success(progress);
    }

    /**
     * 合并文件分片
     * @param fileId 文件唯一标识
     * @return 文件访问URL
     */
    @PostMapping("/merge")
    public ApiResult<String> mergeChunks(@RequestParam String fileId) {
        String fileUrl = uploadService.mergeChunks(fileId);
        return ApiResult.success(fileUrl);
    }
}