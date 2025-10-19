package com.jiaruiblog.quickboxserver.controller;

import com.jiaruiblog.quickboxserver.common.ApiResult;
import com.jiaruiblog.quickboxserver.model.dto.FileInfo;
import com.jiaruiblog.quickboxserver.model.request.ChunkUploadRequest;
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
     * 初始化：
     * 验证用户权限和请求参数。
     * 生成唯一uploadId，在数据库或缓存中创建一条上传记录，状态为uploading，存储文件名、文件大小、分片数等信息。
     * 后续应该 使用浏览器的cookie来判断是来自于同一个浏览器
     * @param filename 文件名
     */
    @PostMapping("/init")
    public ApiResult<UploadSession> initUpload(
            @RequestBody ChunkUploadRequest chunkUploadRequest) {
        UploadSession session = uploadService.initUploadSession(chunkUploadRequest);
        return ApiResult.success(session);
    }


    /**
     * 上传分片（适配vue-simple-uploader的upload接口）
     * @param accessCode 对应vue-simple-uploader的accessCode参数
     * @param chunkNumber 分片序号
     * @param file 分片文件
     */
    @PostMapping("/upload")
    public ApiResult<UploadProgress> uploadChunk(
            @RequestBody ChunkUploadRequest chunkUploadRequest,
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
     * @param accessCode 对应vue-simple-uploader的accessCode参数
     */
    @PostMapping("/merge")
    public ApiResult<String> mergeChunks(
            @RequestParam String accessCode) {  // Simplified parameters
        return ApiResult.success(uploadService.mergeChunks(accessCode));
    }


    /**
     * Get file information by access code
     * @param accessCode 对应vue-simple-uploader的accessCode参数
     */
    @GetMapping("/info/{accessCode}")
    public ApiResult<FileInfo> getFileInfo(@PathVariable String accessCode) {
        return ApiResult.success(uploadService.getFileInfo(accessCode));
    }

    /**
     * Download file by access code
     * @param accessCode 对应vue-simple-uploader的accessCode参数
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