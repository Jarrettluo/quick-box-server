package com.jiaruiblog.quickboxserver.controller;

import com.jiaruiblog.quickboxserver.common.ApiResult;
import com.jiaruiblog.quickboxserver.model.folder.FolderChunkUploadRequest;
import com.jiaruiblog.quickboxserver.model.folder.FolderInfoResponse;
import com.jiaruiblog.quickboxserver.model.folder.FolderUploadRequest;
import com.jiaruiblog.quickboxserver.model.folder.FolderUploadResponse;
import com.jiaruiblog.quickboxserver.service.folder.FolderUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件夹上传控制器
 */
@AllArgsConstructor
@Slf4j
@RestController
@RequestMapping("/api/upload/folder")
@Tag(name = "文件夹上传", description = "文件夹上传和管理API")
public class FolderUploadController {

    private FolderUploadService folderUploadService;

    // STEP1: 初始化文件夹的metadata.json信息；存下来每一个要传递文件的id和code；存储整个文件夹的结构， 后面的每个文件就关联上了。


    // STEP2: 传递每一个单独的文件，先初始化，正常传递
    @Operation(summary = "初始化文件夹上传", description = "创建文件夹上传会话")
    @PostMapping("/init")
    public ApiResult<FolderUploadResponse> initFolderUpload(
            @RequestBody FolderUploadRequest request,
            HttpServletRequest httpRequest) {

        log.info("收到文件夹上传初始化请求: {}", request.getFolderName());

        try {
            // 验证请求
            request.validate();

            // 初始化上传
            FolderUploadResponse response = folderUploadService.initFolderUpload(request);

            // 构建下载URL
            String baseUrl = getBaseUrl(httpRequest);
            response.setDownloadUrl(baseUrl + "/api/upload/folder/download/" + response.getAccessCode());
            response.setInfoUrl(baseUrl + "/api/upload/folder/info/" + response.getAccessCode());

            log.info("文件夹上传初始化成功: {} -> {}", response.getSessionId(), response.getAccessCode());
            return ApiResult.success(response);
        } catch (IllegalArgumentException e) {
            log.error("文件夹上传初始化参数错误", e);
            return ApiResult.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("文件夹上传初始化失败", e);
            return ApiResult.error(500, "文件夹上传初始化失败: " + e.getMessage());
        }
    }

    // STEP3: 每个单独的文件进行分片上传，分片的大小由前端决定
    @Operation(summary = "上传文件夹分片", description = "上传文件夹分片文件")
    @PostMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<FolderUploadResponse> uploadFolderChunk(
            @Parameter(description = "上传会话ID") @RequestParam("sessionId") String sessionId,
            @Parameter(description = "分片序号") @RequestParam("chunkNumber") Integer chunkNumber,
            @Parameter(description = "总分片数") @RequestParam("totalChunks") Integer totalChunks,
            @Parameter(description = "当前分片大小") @RequestParam("currentChunkSize") Long currentChunkSize,
            @Parameter(description = "文件名") @RequestParam("filename") String filename,
            @Parameter(description = "相对路径") @RequestParam(value = "relativePath", required = false) String relativePath,
            @Parameter(description = "分片文件") @RequestParam("file") MultipartFile file) {

        log.info("收到文件夹分片上传请求: {} - {} ({} bytes)", sessionId, chunkNumber, currentChunkSize);

        try {
            // 创建请求对象
            FolderChunkUploadRequest request = new FolderChunkUploadRequest();
            request.setSessionId(sessionId);
            request.setChunkNumber(chunkNumber);
            request.setTotalChunks(totalChunks);
            request.setCurrentChunkSize(currentChunkSize);
            request.setFilename(filename);
            request.setRelativePath(relativePath);
            request.setFile(file);

            // 验证请求
            request.validate();

            // 上传分片
            FolderUploadResponse response = folderUploadService.uploadFolderChunk(request);

            log.debug("文件夹分片上传成功: {} - 分片序号：{} ({} bytes)", sessionId, chunkNumber, currentChunkSize);
            return ApiResult.success(response);
        } catch (IllegalArgumentException e) {
            log.error("文件夹分片上传参数错误", e);
            return ApiResult.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("文件夹分片上传失败", e);
            return ApiResult.error(500, "文件夹分片上传失败: " + e.getMessage());
        }
    }

    // STEP4: 合并每一个独立的文件
    @Operation(summary = "合并文件夹分片", description = "合并所有分片并完成上传")
    @PostMapping("/merge")
    public ApiResult<FolderUploadResponse> mergeFolderChunks(
            @Parameter(description = "上传会话ID") @RequestParam("sessionId") String sessionId) {
        log.info("收到文件夹分片合并请求: {}", sessionId);

        try {
            // 合并分片
            FolderUploadResponse response = folderUploadService.mergeFolderChunks(sessionId);

            log.info("文件夹分片合并成功: {}", sessionId);
            return ApiResult.success(response);
        } catch (Exception e) {
            log.error("文件夹分片合并失败", e);
            return ApiResult.error(500, "文件夹分片合并失败: " + e.getMessage());
        }
    }

    @Operation(summary = "取消文件夹上传", description = "取消正在进行的文件夹上传")
    @PostMapping("/cancel")
    public ApiResult<Void> cancelFolderUpload(
            @Parameter(description = "上传会话ID") @RequestParam("sessionId") String sessionId) {
        log.info("收到文件夹上传取消请求: {}", sessionId);

        try {
            folderUploadService.cancelFolderUpload(sessionId);
            log.info("文件夹上传取消成功: {}", sessionId);
            return ApiResult.success();
        } catch (Exception e) {
            log.error("文件夹上传取消失败", e);
            return ApiResult.error(500, "文件夹上传取消失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取上传进度", description = "获取文件夹上传进度信息")
    @GetMapping("/progress/{sessionId}")
    public ApiResult<FolderUploadResponse> getUploadProgress(
            @Parameter(description = "上传会话ID") @PathVariable String sessionId) {
        log.debug("获取文件夹上传进度: {}", sessionId);

        try {
            FolderUploadResponse response = folderUploadService.getUploadProgress(sessionId);
            return ApiResult.success(response);
        } catch (Exception e) {
            log.error("获取文件夹上传进度失败", e);
            return ApiResult.error(500, "获取文件夹上传进度失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取文件夹信息", description = "根据取件码获取文件夹信息")
    @GetMapping("/info/{accessCode}")
    public ApiResult<FolderInfoResponse> getFolderInfo(
            @Parameter(description = "取件码") @PathVariable String accessCode,
            HttpServletRequest httpRequest) {
        log.debug("获取文件夹信息: {}", accessCode);

        try {
            // 获取文件夹信息
            FolderInfoResponse response = folderUploadService.getFolderInfo(accessCode);

            // 构建URL
            String baseUrl = getBaseUrl(httpRequest);
            response.setDownloadUrl(baseUrl + "/api/upload/folder/download/" + accessCode);

            // 构建文件下载URL
            if (response.getFiles() != null) {
                for (FolderInfoResponse.FileInfo file : response.getFiles()) {
                    file.setDownloadUrl(baseUrl + "/api/upload/folder/file/" + accessCode + "?path=" +
                            URLEncoder.encode(file.getRelativePath(), StandardCharsets.UTF_8));
                }
            }

            return ApiResult.success(response);
        } catch (Exception e) {
            log.error("获取文件夹信息失败", e);
            return ApiResult.error(500, "获取文件夹信息失败: " + e.getMessage());
        }
    }

    // STEP5: 根据第一步取到的信息获取文件夹的详情
    @Operation(summary = "验证取件码", description = "验证取件码是否有效")
    @GetMapping("/validate/{accessCode}")
    public ApiResult<Boolean> validateAccessCode(
            @Parameter(description = "取件码") @PathVariable String accessCode) {
        log.debug("验证取件码: {}", accessCode);

        try {
            boolean isValid = folderUploadService.validateAccessCode(accessCode);
            return ApiResult.success(isValid);
        } catch (Exception e) {
            log.error("验证取件码失败", e);
            return ApiResult.error(500, "验证取件码失败: " + e.getMessage());
        }
    }

    // STEP6: 将文件夹进行合并成zip后下载
    @Operation(summary = "下载文件夹", description = "下载文件夹为ZIP压缩包")
    @GetMapping("/download/{accessCode}")
    public ResponseEntity<InputStreamResource> downloadFolder(
            @Parameter(description = "取件码") @PathVariable String accessCode) {
        log.info("下载文件夹: {}", accessCode);

        try {
            // 获取文件夹信息
            FolderInfoResponse folderInfo = folderUploadService.getFolderInfo(accessCode);
            if (folderInfo == null) {
                return ResponseEntity.notFound().build();
            }

            // 检查是否可下载
            if (!folderInfo.isDownloadable()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            // 下载文件夹
            InputStream zipStream = folderUploadService.downloadFolderAsZip(accessCode);

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            String filename = URLEncoder.encode(folderInfo.getFolderName() + ".zip", StandardCharsets.UTF_8);
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.add(HttpHeaders.PRAGMA, "no-cache");
            headers.add(HttpHeaders.EXPIRES, "0");

            log.info("开始下载文件夹: {} -> {}", accessCode, filename);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(zipStream));
        } catch (Exception e) {
            log.error("下载文件夹失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "清理过期文件夹", description = "清理所有过期的文件夹")
    @PostMapping("/cleanup")
    public ApiResult<Void> cleanupExpiredFolders() {
        log.info("清理过期文件夹");

        try {
            folderUploadService.cleanupExpiredFolders();
            log.info("清理过期文件夹完成");
            return ApiResult.success();
        } catch (Exception e) {
            log.error("清理过期文件夹失败", e);
            return ApiResult.error(500, "清理过期文件夹失败: " + e.getMessage());
        }
    }

    @Operation(summary = "删除文件夹", description = "根据取件码删除文件夹")
    @DeleteMapping("/{accessCode}")
    public ApiResult<Void> deleteFolder(
            @Parameter(description = "取件码") @PathVariable String accessCode) {
        log.info("删除文件夹: {}", accessCode);

        try {
            folderUploadService.deleteFolder(accessCode);
            log.info("删除文件夹成功: {}", accessCode);
            return ApiResult.success();
        } catch (Exception e) {
            log.error("删除文件夹失败", e);
            return ApiResult.error(500, "删除文件夹失败: " + e.getMessage());
        }
    }

    @Operation(summary = "批量删除文件夹", description = "批量删除多个文件夹")
    @PostMapping("/batch-delete")
    public ApiResult<Void> batchDeleteFolders(
            @Parameter(description = "取件码列表") @RequestBody java.util.List<String> accessCodes) {
        log.info("批量删除文件夹: {} 个", accessCodes.size());

        try {
            folderUploadService.batchDeleteFolders(accessCodes);
            log.info("批量删除文件夹成功: {} 个", accessCodes.size());
            return ApiResult.success();
        } catch (Exception e) {
            log.error("批量删除文件夹失败", e);
            return ApiResult.error(500, "批量删除文件夹失败: " + e.getMessage());
        }
    }

    /**
     * 获取基础URL
     * 这里是后端的请求地址
     */
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();

        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);

        if (("http".equals(scheme) && serverPort != 80) ||
            ("https".equals(scheme) && serverPort != 443)) {
            url.append(":").append(serverPort);
        }

        url.append(contextPath);
        return url.toString();
    }

    /**
     * 处理文件上传异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResult<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("参数错误", e);
        return ApiResult.error(400, e.getMessage());
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception e) {
        log.error("服务器错误", e);
        return ApiResult.error(500, "服务器内部错误: " + e.getMessage());
    }
}