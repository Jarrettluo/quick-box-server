package com.jiaruiblog.quickboxserver.controller;

import com.jiaruiblog.quickboxserver.common.ApiResult;
import com.jiaruiblog.quickboxserver.model.dto.FileInfo;
import com.jiaruiblog.quickboxserver.model.request.ChunkUploadRequest;
import com.jiaruiblog.quickboxserver.model.response.FileCheckResult;
import com.jiaruiblog.quickboxserver.model.response.UploadProgress;
import com.jiaruiblog.quickboxserver.model.response.UploadSession;
import com.jiaruiblog.quickboxserver.service.FileUploadService;
import jakarta.annotation.Resource;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
            @RequestParam Integer chunkNumber) {
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
            @RequestParam String accessCode,
            @RequestParam Integer chunkNumber,
            @RequestParam("file") MultipartFile file) {

        ChunkUploadRequest request = new ChunkUploadRequest(
                accessCode,
                chunkNumber,
                null,  // Optional fields can be null
                null,
                null,
                null,
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
            @RequestParam String accessCode) {  // Simplified parameters
        return ApiResult.success(uploadService.mergeChunks(accessCode));
    }

    /**
     * 获取上传进度（可选，用于前端展示）
     */
    @GetMapping("/progress")
    public ApiResult<UploadProgress> getProgress(
            @RequestParam String accessCode) {  // Changed from identifier
        return ApiResult.success(uploadService.getUploadProgress(accessCode, null));
    }

    /**
     * Get file information by access code
     */
    @GetMapping("/info/{accessCode}")
    public ApiResult<FileInfo> getFileInfo(@PathVariable String accessCode) {
        return ApiResult.success(uploadService.getFileInfo(accessCode));
    }

    /**
     * Download file by access code
     */
    @GetMapping("/download/{accessCode}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String accessCode) throws IOException {
        File file = uploadService.getFileByAccessCode(accessCode);
        Path path = file.toPath();
        Resource resource = (Resource) new InputStreamResource(Files.newInputStream(path));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getName() + "\"")
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}