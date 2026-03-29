package com.jiaruiblog.quickboxserver.controller;

import com.jiaruiblog.quickboxserver.common.ApiResult;
import com.jiaruiblog.quickboxserver.model.dto.FileInfo;
import com.jiaruiblog.quickboxserver.model.request.ChunkUploadRequest;
import com.jiaruiblog.quickboxserver.model.response.UploadProgress;
import com.jiaruiblog.quickboxserver.model.response.UploadSession;
import com.jiaruiblog.quickboxserver.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/upload")
@Tag(name = "文件上传", description = "文件上传、下载和管理接口")
public class FileUploadController {

    @Resource
    private FileUploadService uploadService;


    @Operation(summary = "初始化上传", description = "验证用户权限和请求参数，生成唯一uploadId，创建上传记录")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "初始化成功",
                    content = @Content(schema = @Schema(implementation = UploadSession.class)))
    })
    @PostMapping("/init")
    public ApiResult<UploadSession> initUpload(
            @Parameter(description = "分片上传请求") @RequestBody ChunkUploadRequest chunkUploadRequest) {
        UploadSession session = uploadService.initUploadSession(chunkUploadRequest);
        return ApiResult.success(session);
    }


    @Operation(summary = "上传分片", description = "上传文件分片，适配vue-simple-uploader的upload接口")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "上传成功",
                    content = @Content(schema = @Schema(implementation = UploadProgress.class)))
    })
    @PostMapping("/upload")
    public ApiResult<UploadProgress> uploadChunk(
            @Parameter(description = "分片上传请求，包含分片序号和uploadId") @ModelAttribute ChunkUploadRequest chunkUploadRequest,
            @Parameter(description = "分片文件") @RequestParam("file") MultipartFile file) {

        UploadProgress progress = uploadService.uploadChunk(chunkUploadRequest, file);
        return ApiResult.success(progress);
    }

    @Operation(summary = "合并分片", description = "合并所有分片文件，适配vue-simple-uploader的merge接口")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "合并成功")
    })
    @PostMapping("/merge")
    public ApiResult<String> mergeChunks(
            @Parameter(description = "取件码，对应vue-simple-uploader的accessCode参数") @RequestParam String accessCode) {
        return ApiResult.success("success", uploadService.mergeChunks(accessCode));
    }


    @Operation(summary = "获取文件信息", description = "通过取件码获取文件信息")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "获取成功",
                    content = @Content(schema = @Schema(implementation = FileInfo.class)))
    })
    @GetMapping("/info/{accessCode}")
    public ApiResult<FileInfo> getFileInfo(
            @Parameter(description = "取件码") @PathVariable String accessCode) {
        return ApiResult.success(uploadService.getFileInfo(accessCode));
    }

    @Operation(summary = "下载文件", description = "通过取件码下载文件，取件码使用后失效")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "下载成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "文件不存在或已过期")
    })
    @GetMapping("/download/{accessCode}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFile(
            @Parameter(description = "取件码") @PathVariable String accessCode) throws IOException {
        File file = uploadService.getFileByAccessCode(accessCode);
        org.springframework.core.io.Resource resource = new FileSystemResource(file);

        String encodedFilename = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        String contentDisposition = "attachment; filename=\"" + encodedFilename + "\"; filename*=UTF-8''" + encodedFilename;

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Content-Disposition, Accept-Ranges, Content-Length, Cache-Control")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(summary = "下载文件（带文件名）", description = "通过取件码下载文件，文件名仅用于URL兼容性")
    @GetMapping("/download/{accessCode}/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadFileWithName(
            @Parameter(description = "取件码") @PathVariable String accessCode,
            @Parameter(description = "文件名（忽略）") @PathVariable String filename) throws IOException {
        return downloadFile(accessCode);
    }
}