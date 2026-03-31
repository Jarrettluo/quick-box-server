package com.jiaruiblog.quickboxserver.storage.strategy;

import com.jiaruiblog.quickboxserver.storage.StorageService;
import com.jiaruiblog.quickboxserver.storage.StorageServiceFactory;
import com.jiaruiblog.quickboxserver.storage.model.StorageStats;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 简单存储策略
 * 直接使用 StorageServiceFactory 的主存储服务
 */
@Slf4j
@Component
public class SimpleStorageStrategy implements StorageStrategy {

    @Autowired
    private StorageServiceFactory storageServiceFactory;

    private StorageService primaryStorageService;

    @PostConstruct
    public void init() {
        this.primaryStorageService = storageServiceFactory.getPrimaryStorageService();
        log.info("简单存储策略初始化完成，使用主存储服务: {}", primaryStorageService.getStorageName());
    }

    @Override
    public String getStrategyName() {
        return "SimpleStorageStrategy";
    }

    @Override
    public String getStrategyDescription() {
        return "简单存储策略，直接使用主存储服务";
    }

    @Override
    public StorageService selectStorageService() {
        return primaryStorageService;
    }

    @Override
    public StorageService selectStorageServiceForFolder() {
        return primaryStorageService;
    }

    @Override
    public List<StorageService> getAllAvailableStorageServices() {
        return List.of(primaryStorageService);
    }

    @Override
    public StorageService getPrimaryStorageService() {
        return primaryStorageService;
    }

    @Override
    public List<StorageService> getBackupStorageServices() {
        return List.of();
    }

    @Override
    public void switchPrimaryStorageService(String storageServiceName) {
        log.warn("SimpleStorageStrategy 不支持动态切换存储服务");
    }

    @Override
    public StorageStats getStrategyStats() {
        return primaryStorageService.getStorageStats();
    }

    @Override
    public boolean isStorageServiceAvailable(String storageServiceName) {
        return primaryStorageService.getStorageName().equals(storageServiceName)
            && primaryStorageService.isAvailable();
    }

    @Override
    public Map<String, Boolean> getStorageHealthStatus() {
        return Map.of(primaryStorageService.getStorageName(), primaryStorageService.isAvailable());
    }

    @Override
    public void reloadStrategy() {
        // 重新从工厂获取主存储服务
        this.primaryStorageService = storageServiceFactory.getPrimaryStorageService();
        log.info("存储策略重新加载完成，当前主存储服务: {}", primaryStorageService.getStorageName());
    }

    @Override
    public boolean validateStrategy() {
        return primaryStorageService != null && primaryStorageService.isAvailable();
    }

    @Override
    public Map<String, Object> getStrategyConfig() {
        return Map.of(
            "strategyName", getStrategyName(),
            "primaryStorageService", primaryStorageService.getStorageName(),
            "available", primaryStorageService.isAvailable()
        );
    }
}
