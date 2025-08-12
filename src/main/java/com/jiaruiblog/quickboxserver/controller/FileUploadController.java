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
    private FileUploadService uploadService;

    /**
     * 文件上传前检查（适配vue-simple-uploader的秒传验证）
     */
    @GetMapping("/verify")
    public ApiResult<FileCheckResult> verifyFile(
            @RequestParam String identifier, // 对应vue-simple-uploader的identifier参数
            @RequestParam String filename,
            @RequestParam long size) {
        FileCheckResult result = uploadService.prepareUpload(
                identifier,
                filename,
                size
        );
        return ApiResult.success(result);
    }

    /**
     * 初始化上传（适配vue-simple-uploader）
     */
    @PostMapping("/init")
    public ApiResult<UploadSession> initUpload(
            @RequestParam String identifier,
            @RequestParam String filename) {
        UploadSession session = uploadService.initUploadSession(identifier, filename);
        return ApiResult.success(session);
    }

    /**
     * 检查分片上传状态（适配vue-simple-uploader的chunkCheck接口）
     */
    @GetMapping("/chunkCheck")
    public ApiResult<Boolean> checkChunk(
            @RequestParam String identifier,
            @RequestParam Integer chunkNumber,
            @RequestParam(required = false) Long chunkSize) {
        UploadProgress progress = uploadService.getUploadProgress(identifier, chunkNumber);
        // 返回该分片是否已上传
        boolean exists = progress.uploadedChunkNumbers().contains(chunkNumber);
        return ApiResult.success(exists);
    }

    /**
     * 上传分片（适配vue-simple-uploader的upload接口）
     */
    @PostMapping("/upload")
    public ApiResult<UploadProgress> uploadChunk(
            @RequestParam String identifier,
            @RequestParam Integer chunkNumber,
            @RequestParam(required = false) Long chunkSize,
            @RequestParam(required = false) Long currentChunkSize,
            @RequestParam(required = false) Integer totalChunks,
            @RequestParam(required = false) Long totalSize,
            @RequestParam("file") MultipartFile file) {
        
        ChunkUploadRequest request = new ChunkUploadRequest(
                identifier,
                chunkNumber,
                chunkSize,
                currentChunkSize,
                totalChunks,
                totalSize,
                file.getOriginalFilename(),
                null,
                file.getContentType()
        );
        
        UploadProgress progress = uploadService.uploadChunk(request, file);
        return ApiResult.success(progress);
    }

    /**
     * 合并分片（适配vue-simple-uploader的merge接口）
     */
    @PostMapping("/merge")
    public ApiResult<String> mergeChunks(
            @RequestParam String identifier,
            @RequestParam String filename,
            @RequestParam(required = false) Integer totalChunks,
            @RequestParam(required = false) Long totalSize) {
        
        String fileUrl = uploadService.mergeChunks(identifier);
        return ApiResult.success(fileUrl);
    }

    /**
     * 获取上传进度（可选，用于前端展示）
     */
    @GetMapping("/progress")
    public ApiResult<UploadProgress> getProgress(
            @RequestParam String identifier) {
        UploadProgress progress = uploadService.getUploadProgress(identifier, null);
        return ApiResult.success(progress);
    }
}