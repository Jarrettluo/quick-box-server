package com.jiaruiblog.quickboxserver.controller;

import com.jiaruiblog.quickboxserver.common.ApiResult;
import com.jiaruiblog.quickboxserver.model.admin.StorageBackendInfo;
import com.jiaruiblog.quickboxserver.model.admin.StrategyRequest;
import com.jiaruiblog.quickboxserver.model.admin.WeightRequest;
import com.jiaruiblog.quickboxserver.storage.StorageService;
import com.jiaruiblog.quickboxserver.storage.StorageServiceFactory;
import com.jiaruiblog.quickboxserver.storage.model.StorageConfig;
import com.jiaruiblog.quickboxserver.storage.model.StorageHealth;
import com.jiaruiblog.quickboxserver.storage.model.StorageStats;
import com.jiaruiblog.quickboxserver.storage.model.StorageUsage;
import com.jiaruiblog.quickboxserver.storage.strategy.StorageStrategy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存储管理控制器
 * 提供存储后端的管理和监控API
 */
@AllArgsConstructor
@Slf4j
@RestController
@RequestMapping("/api/storage")
@Tag(name = "存储管理", description = "存储后端管理和监控API")
public class StorageAdminController {

    private StorageServiceFactory storageServiceFactory;

    private StorageStrategy storageStrategy;

    @Operation(summary = "获取存储后端列表", description = "获取所有配置的存储后端信息")
    @GetMapping("/backends")
    public ApiResult<List<StorageBackendInfo>> getStorageBackends() {
        log.debug("获取存储后端列表");

        try {
            Map<String, StorageService> allServices = storageServiceFactory.getAllStorageServices();
            List<StorageBackendInfo> backends = allServices.values().stream()
                .map(this::convertToBackendInfo)
                .toList();

            return ApiResult.success(backends);
        } catch (Exception e) {
            log.error("获取存储后端列表失败", e);
            return ApiResult.error(500, "获取存储后端列表失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取存储后端状态", description = "获取指定存储后端的健康状态")
    @GetMapping("/status/{backendName}")
    public ApiResult<StorageHealth> getStorageStatus(
            @Parameter(description = "存储后端名称") @PathVariable String backendName) {
        log.debug("获取存储后端状态: {}", backendName);

        try {
            StorageService storageService = storageServiceFactory.getStorageService(backendName);
            StorageHealth health = storageService.getHealthStatus();
            return ApiResult.success(health);
        } catch (Exception e) {
            log.error("获取存储后端状态失败", e);
            return ApiResult.error(500, "获取存储后端状态失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取所有存储后端状态", description = "获取所有存储后端的健康状态")
    @GetMapping("/status")
    public ApiResult<Map<String, StorageHealth>> getAllStorageStatus() {
        log.debug("获取所有存储后端状态");

        try {
            Map<String, StorageService> allServices = storageServiceFactory.getAllStorageServices();
            Map<String, StorageHealth> statusMap = new HashMap<>();

            for (Map.Entry<String, StorageService> entry : allServices.entrySet()) {
                try {
                    StorageHealth health = entry.getValue().getHealthStatus();
                    statusMap.put(entry.getKey(), health);
                } catch (Exception e) {
                    log.error("获取存储后端状态失败: {}", entry.getKey(), e);
                    StorageHealth errorHealth = new StorageHealth();
                    errorHealth.setStorageName(entry.getKey());
                    errorHealth.setStatus(StorageHealth.HealthStatus.UNHEALTHY);
                    errorHealth.setErrorMessage("获取状态失败: " + e.getMessage());
                    statusMap.put(entry.getKey(), errorHealth);
                }
            }

            return ApiResult.success(statusMap);
        } catch (Exception e) {
            log.error("获取所有存储后端状态失败", e);
            return ApiResult.error(500, "获取所有存储后端状态失败: " + e.getMessage());
        }
    }

    @Operation(summary = "切换存储策略", description = "切换存储策略配置（当前版本不支持动态切换）")
    @PostMapping("/strategy")
    public ApiResult<String> switchStorageStrategy(
            @Parameter(description = "策略请求") @RequestBody StrategyRequest request) {
        log.info("切换存储策略请求: {}", request.getStrategyType());

        // 当前版本不支持动态切换存储策略，配置从 YAML 读取
        // 如需切换存储类型，请修改配置文件并重启应用
        return ApiResult.error(400, "当前版本不支持动态切换存储策略，请修改配置文件并重启应用");
    }

    @Operation(summary = "获取存储统计信息", description = "获取存储使用统计信息")
    @GetMapping("/stats")
    public ApiResult<Map<String, Object>> getStorageStats() {
        log.debug("获取存储统计信息");

        try {
            Map<String, Object> stats = new HashMap<>();

            // 获取所有存储服务的统计
            Map<String, StorageService> allServices = storageServiceFactory.getAllStorageServices();
            Map<String, StorageStats> serviceStats = new HashMap<>();

            for (Map.Entry<String, StorageService> entry : allServices.entrySet()) {
                try {
                    StorageStats stat = entry.getValue().getStorageStats();
                    serviceStats.put(entry.getKey(), stat);
                } catch (Exception e) {
                    log.error("获取存储统计失败: {}", entry.getKey(), e);
                }
            }

            stats.put("services", serviceStats);

            // 获取策略统计
            try {
                StorageStats strategyStats = storageStrategy.getStrategyStats();
                stats.put("strategy", strategyStats);
            } catch (Exception e) {
                log.error("获取策略统计失败", e);
            }

            // 获取存储使用情况
            try {
                Map<String, Object> usage = storageServiceFactory.getStorageUsage();
                stats.put("usage", usage);
            } catch (Exception e) {
                log.error("获取存储使用情况失败", e);
            }

            return ApiResult.success(stats);
        } catch (Exception e) {
            log.error("获取存储统计信息失败", e);
            return ApiResult.error(500, "获取存储统计信息失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取存储使用情况", description = "获取存储空间使用情况")
    @GetMapping("/usage")
    public ApiResult<Map<String, StorageUsage>> getStorageUsage() {
        log.debug("获取存储使用情况");

        try {
            Map<String, StorageService> allServices = storageServiceFactory.getAllStorageServices();
            Map<String, StorageUsage> usageMap = new HashMap<>();

            for (Map.Entry<String, StorageService> entry : allServices.entrySet()) {
                try {
                    StorageUsage usage = entry.getValue().getStorageUsage();
                    usageMap.put(entry.getKey(), usage);
                } catch (Exception e) {
                    log.error("获取存储使用情况失败: {}", entry.getKey(), e);
                }
            }

            return ApiResult.success(usageMap);
        } catch (Exception e) {
            log.error("获取存储使用情况失败", e);
            return ApiResult.error(500, "获取存储使用情况失败: " + e.getMessage());
        }
    }

    @Operation(summary = "切换主存储服务", description = "切换主存储服务（当前版本不支持）")
    @PostMapping("/switch-primary")
    public ApiResult<String> switchPrimaryStorageService(
            @Parameter(description = "存储服务名称") @RequestParam String storageServiceName) {
        log.info("切换主存储服务请求: {}", storageServiceName);

        // 当前版本不支持动态切换主存储服务
        return ApiResult.error(400, "当前版本不支持动态切换主存储服务");
    }

    @Operation(summary = "重新加载存储服务", description = "重新加载存储服务配置")
    @PostMapping("/reload/{backendName}")
    public ApiResult<String> reloadStorageService(
            @Parameter(description = "存储后端名称") @PathVariable String backendName,
            @Parameter(description = "存储配置") @RequestBody StorageConfig newConfig) {
        log.info("重新加载存储服务: {}", backendName);

        try {
            storageServiceFactory.reloadStorageService(backendName, newConfig);
            log.info("存储服务重新加载成功: {}", backendName);
            return ApiResult.success("存储服务重新加载成功");
        } catch (Exception e) {
            log.error("重新加载存储服务失败", e);
            return ApiResult.error(500, "重新加载存储服务失败: " + e.getMessage());
        }
    }

    @Operation(summary = "清理存储服务", description = "清理所有存储服务的过期数据")
    @PostMapping("/cleanup")
    public ApiResult<String> cleanupStorageServices() {
        log.info("清理存储服务");

        try {
            storageServiceFactory.cleanupAll();
            log.info("存储服务清理完成");
            return ApiResult.success("存储服务清理完成");
        } catch (Exception e) {
            log.error("清理存储服务失败", e);
            return ApiResult.error(500, "清理存储服务失败: " + e.getMessage());
        }
    }

    @Operation(summary = "获取存储策略配置", description = "获取当前存储策略配置")
    @GetMapping("/strategy/config")
    public ApiResult<Map<String, Object>> getStrategyConfig() {
        log.debug("获取存储策略配置");

        try {
            Map<String, Object> config = storageStrategy.getStrategyConfig();
            return ApiResult.success(config);
        } catch (Exception e) {
            log.error("获取存储策略配置失败", e);
            return ApiResult.error(500, "获取存储策略配置失败: " + e.getMessage());
        }
    }

    @Operation(summary = "验证存储策略", description = "验证存储策略配置是否有效")
    @GetMapping("/strategy/validate")
    public ApiResult<Boolean> validateStrategy() {
        log.debug("验证存储策略");

        try {
            boolean isValid = storageStrategy.validateStrategy();
            return ApiResult.success(isValid);
        } catch (Exception e) {
            log.error("验证存储策略失败", e);
            return ApiResult.error(500, "验证存储策略失败: " + e.getMessage());
        }
    }

    @Operation(summary = "设置存储服务权重", description = "设置存储服务的负载均衡权重（当前版本不支持）")
    @PostMapping("/weight")
    public ApiResult<String> setStorageServiceWeight(
            @Parameter(description = "权重请求") @RequestBody WeightRequest request) {
        log.info("设置存储服务权重请求: {} -> {}", request.getStorageServiceName(), request.getWeight());

        // 当前版本不支持设置存储服务权重
        return ApiResult.error(400, "当前版本不支持设置存储服务权重");
    }

    @Operation(summary = "获取存储健康状态", description = "获取所有存储服务的健康状态")
    @GetMapping("/health")
    public ApiResult<Map<String, Boolean>> getStorageHealth() {
        log.debug("获取存储健康状态");

        try {
            Map<String, Boolean> healthStatus = storageStrategy.getStorageHealthStatus();
            return ApiResult.success(healthStatus);
        } catch (Exception e) {
            log.error("获取存储健康状态失败", e);
            return ApiResult.error(500, "获取存储健康状态失败: " + e.getMessage());
        }
    }

    @Operation(summary = "重置存储统计", description = "重置所有存储服务的统计信息")
    @PostMapping("/stats/reset")
    public ApiResult<String> resetStorageStats() {
        log.info("重置存储统计");

        try {
            Map<String, StorageService> allServices = storageServiceFactory.getAllStorageServices();
            for (StorageService service : allServices.values()) {
                try {
                    service.resetStats();
                } catch (Exception e) {
                    log.error("重置存储统计失败: {}", service.getStorageName(), e);
                }
            }

            log.info("存储统计重置完成");
            return ApiResult.success("存储统计重置完成");
        } catch (Exception e) {
            log.error("重置存储统计失败", e);
            return ApiResult.error(500, "重置存储统计失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private StorageBackendInfo convertToBackendInfo(StorageService storageService) {
        StorageBackendInfo info = new StorageBackendInfo();
        info.setName(storageService.getStorageName());
        info.setType(storageService.getStorageType());
        info.setConfig(storageService.getConfig());
        info.setAvailable(storageService.isAvailable());

        try {
            StorageHealth health = storageService.getHealthStatus();
            info.setHealthStatus(health.getStatus().name());
            info.setErrorMessage(health.getErrorMessage());
        } catch (Exception e) {
            info.setHealthStatus("UNKNOWN");
            info.setErrorMessage("获取健康状态失败: " + e.getMessage());
        }

        return info;
    }
}