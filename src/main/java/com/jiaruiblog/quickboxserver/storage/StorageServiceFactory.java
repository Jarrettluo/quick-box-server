package com.jiaruiblog.quickboxserver.storage;

import com.jiaruiblog.quickboxserver.storage.impl.LocalFileStorageService;
import com.jiaruiblog.quickboxserver.storage.model.StorageConfig;
import com.jiaruiblog.quickboxserver.storage.model.StorageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存储服务工厂
 * 负责创建和管理所有存储服务实例
 */
@Slf4j
@Component
public class StorageServiceFactory {

    private final Map<String, StorageService> storageServices = new ConcurrentHashMap<>();
    private final Map<StorageType, StorageService> defaultServices = new ConcurrentHashMap<>();

    private StorageService primaryStorageService;
    private final Map<String, StorageConfig> storageConfigs = new HashMap<>();

    /**
     * 初始化存储服务
     */
    @PostConstruct
    public void init() {
        log.info("初始化存储服务工厂");

        // 这里应该从配置文件中加载所有存储配置
        // 暂时创建默认的本地存储服务
        initDefaultLocalStorage();

        log.info("存储服务工厂初始化完成，共 {} 个存储服务", storageServices.size());
    }

    /**
     * 初始化默认本地存储服务
     */
    private void initDefaultLocalStorage() {
        try {
            StorageConfig config = new StorageConfig();
            config.setType(StorageType.LOCAL);
            config.setName("default-local");
            config.setEnabled(true);
            config.setPrimary(true);
            config.setPriority(1);

            StorageConfig.LocalConfig localConfig = new StorageConfig.LocalConfig();
            localConfig.setBasePath(System.getProperty("user.home") + "/quickbox/storage");
            localConfig.setChunkPath(System.getProperty("user.home") + "/quickbox/chunks");
            localConfig.setFilePath(System.getProperty("user.home") + "/quickbox/files");
            localConfig.setCreateDirectories(true);
            localConfig.setUseTempFiles(true);
            localConfig.setTempFilePrefix("quickbox_");

            config.setLocalConfig(localConfig);

            StorageService storageService = createStorageService(config);
            registerStorageService(storageService);

            log.info("创建默认本地存储服务: {}", storageService.getStorageName());
        } catch (Exception e) {
            log.error("初始化默认本地存储服务失败", e);
        }
    }

    /**
     * 创建存储服务
     */
    public StorageService createStorageService(StorageConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("存储配置不能为空");
        }

        if (!config.isEnabled()) {
            throw new IllegalArgumentException("存储服务未启用: " + config.getName());
        }

        StorageType type = config.getType();
        String name = config.getName();

        log.info("创建存储服务: {} ({})", name, type.getDescription());

        StorageService storageService;
        switch (type) {
            case LOCAL:
                storageService = new LocalFileStorageService(config);
                break;
            case S3:
            case MINIO:
                // TODO: 实现S3/MinIO存储服务
                throw new UnsupportedOperationException("S3/MinIO存储服务暂未实现");
            case WEBDAV:
                // TODO: 实现WebDAV存储服务
                throw new UnsupportedOperationException("WebDAV存储服务暂未实现");
            case NAS:
                // TODO: 实现NAS存储服务
                throw new UnsupportedOperationException("NAS存储服务暂未实现");
            case GPFS:
                // TODO: 实现GPFS存储服务
                throw new UnsupportedOperationException("GPFS存储服务暂未实现");
            default:
                throw new IllegalArgumentException("不支持的存储类型: " + type);
        }

        return storageService;
    }

    /**
     * 注册存储服务
     */
    public void registerStorageService(StorageService storageService) {
        if (storageService == null) {
            throw new IllegalArgumentException("存储服务不能为空");
        }

        String name = storageService.getStorageName();
        StorageType type = storageService.getStorageType();

        if (storageServices.containsKey(name)) {
            throw new IllegalArgumentException("存储服务已存在: " + name);
        }

        storageServices.put(name, storageService);
        storageConfigs.put(name, storageService.getConfig());

        // 设置默认服务
        if (!defaultServices.containsKey(type)) {
            defaultServices.put(type, storageService);
        }

        // 设置主存储服务
        StorageConfig config = storageService.getConfig();
        if (config.isPrimary()) {
            if (primaryStorageService != null) {
                log.warn("已存在主存储服务: {}, 新的主存储服务: {}",
                    primaryStorageService.getStorageName(), name);
            }
            primaryStorageService = storageService;
            log.info("设置主存储服务: {}", name);
        }

        log.info("注册存储服务成功: {} ({})", name, type.getDescription());
    }

    /**
     * 获取存储服务
     */
    public StorageService getStorageService(String name) {
        StorageService service = storageServices.get(name);
        if (service == null) {
            throw new IllegalArgumentException("存储服务不存在: " + name);
        }
        return service;
    }

    /**
     * 获取指定类型的存储服务
     */
    public StorageService getStorageService(StorageType type) {
        StorageService service = defaultServices.get(type);
        if (service == null) {
            throw new IllegalArgumentException("该类型的存储服务不存在: " + type);
        }
        return service;
    }

    /**
     * 获取主存储服务
     */
    public StorageService getPrimaryStorageService() {
        if (primaryStorageService == null) {
            throw new IllegalStateException("未设置主存储服务");
        }
        return primaryStorageService;
    }

    /**
     * 获取所有存储服务
     */
    public Map<String, StorageService> getAllStorageServices() {
        return new HashMap<>(storageServices);
    }

    /**
     * 获取所有启用的存储服务
     */
    public Map<String, StorageService> getEnabledStorageServices() {
        Map<String, StorageService> enabledServices = new HashMap<>();
        for (Map.Entry<String, StorageService> entry : storageServices.entrySet()) {
            StorageConfig config = entry.getValue().getConfig();
            if (config.isEnabled()) {
                enabledServices.put(entry.getKey(), entry.getValue());
            }
        }
        return enabledServices;
    }

    /**
     * 获取存储服务配置
     */
    public StorageConfig getStorageConfig(String name) {
        return storageConfigs.get(name);
    }

    /**
     * 获取所有存储配置
     */
    public Map<String, StorageConfig> getAllStorageConfigs() {
        return new HashMap<>(storageConfigs);
    }

    /**
     * 检查存储服务是否存在
     */
    public boolean containsStorageService(String name) {
        return storageServices.containsKey(name);
    }

    /**
     * 检查存储服务是否可用
     */
    public boolean isStorageServiceAvailable(String name) {
        StorageService service = storageServices.get(name);
        if (service == null) {
            return false;
        }
        return service.isAvailable();
    }

    /**
     * 移除存储服务
     */
    public void removeStorageService(String name) {
        StorageService service = storageServices.remove(name);
        if (service != null) {
            storageConfigs.remove(name);

            // 清理默认服务
            StorageType type = service.getStorageType();
            if (defaultServices.get(type) == service) {
                defaultServices.remove(type);
            }

            // 清理主存储服务
            if (primaryStorageService == service) {
                primaryStorageService = null;
            }

            log.info("移除存储服务: {}", name);
        }
    }

    /**
     * 切换主存储服务
     */
    public void switchPrimaryStorageService(String name) {
        StorageService service = getStorageService(name);
        StorageConfig config = service.getConfig();

        // 更新配置
        config.setPrimary(true);

        // 更新其他服务的配置
        for (StorageService otherService : storageServices.values()) {
            if (otherService != service) {
                StorageConfig otherConfig = otherService.getConfig();
                otherConfig.setPrimary(false);
            }
        }

        primaryStorageService = service;
        log.info("切换主存储服务为: {}", name);
    }

    /**
     * 重新加载存储服务
     */
    public void reloadStorageService(String name, StorageConfig newConfig) {
        if (!storageServices.containsKey(name)) {
            throw new IllegalArgumentException("存储服务不存在: " + name);
        }

        // 移除旧服务
        removeStorageService(name);

        // 创建新服务
        StorageService newService = createStorageService(newConfig);
        registerStorageService(newService);

        log.info("重新加载存储服务: {}", name);
    }

    /**
     * 获取存储服务健康状态
     */
    public Map<String, Boolean> getStorageHealthStatus() {
        Map<String, Boolean> healthStatus = new HashMap<>();
        for (Map.Entry<String, StorageService> entry : storageServices.entrySet()) {
            healthStatus.put(entry.getKey(), entry.getValue().isAvailable());
        }
        return healthStatus;
    }

    /**
     * 获取存储服务统计信息
     */
    public Map<String, Object> getStorageStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        for (Map.Entry<String, StorageService> entry : storageServices.entrySet()) {
            statistics.put(entry.getKey(), entry.getValue().getStorageStats());
        }
        return statistics;
    }

    /**
     * 获取存储服务使用情况
     */
    public Map<String, Object> getStorageUsage() {
        Map<String, Object> usage = new HashMap<>();
        for (Map.Entry<String, StorageService> entry : storageServices.entrySet()) {
            usage.put(entry.getKey(), entry.getValue().getStorageUsage());
        }
        return usage;
    }

    /**
     * 清理所有存储服务
     */
    public void cleanupAll() {
        log.info("开始清理所有存储服务");

        for (StorageService service : storageServices.values()) {
            try {
                service.cleanupExpiredSessions(java.time.LocalDateTime.now().minusDays(1));
            } catch (Exception e) {
                log.error("清理存储服务失败: {}", service.getStorageName(), e);
            }
        }

        log.info("清理所有存储服务完成");
    }

    /**
     * 关闭所有存储服务
     */
    public void shutdown() {
        log.info("开始关闭所有存储服务");

        // 执行清理
        cleanupAll();

        // 清空所有服务
        storageServices.clear();
        defaultServices.clear();
        storageConfigs.clear();
        primaryStorageService = null;

        log.info("关闭所有存储服务完成");
    }
}