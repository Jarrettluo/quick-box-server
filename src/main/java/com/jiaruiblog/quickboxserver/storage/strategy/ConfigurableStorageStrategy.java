package com.jiaruiblog.quickboxserver.storage.strategy;

import com.jiaruiblog.quickboxserver.storage.StorageService;
import com.jiaruiblog.quickboxserver.storage.StorageServiceFactory;
import com.jiaruiblog.quickboxserver.storage.model.StorageStats;
import com.jiaruiblog.quickboxserver.storage.model.StorageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可配置存储策略
 * 支持多种策略组合：主备模式、负载均衡、分级存储等
 */
@Slf4j
@Component
public class ConfigurableStorageStrategy implements StorageStrategy {

    @Autowired
    private StorageServiceFactory storageServiceFactory;

    private final Map<String, Object> strategyConfig = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);

    private StorageService primaryStorageService;
    private final List<StorageService> backupStorageServices = new ArrayList<>();
    private final List<StorageService> allStorageServices = new ArrayList<>();

    private StrategyType currentStrategyType = StrategyType.PRIMARY_BACKUP;
    private LoadBalanceAlgorithm loadBalanceAlgorithm = LoadBalanceAlgorithm.ROUND_ROBIN;
    private final Map<StorageService, Long> storageServiceWeights = new ConcurrentHashMap<>();
    private int currentRoundRobinIndex = 0;

    /**
     * 策略类型枚举
     */
    public enum StrategyType {
        PRIMARY_BACKUP("主备模式", "使用主存储服务，主服务不可用时使用备份服务"),
        LOAD_BALANCE("负载均衡", "在多个存储服务间均衡分配负载"),
        TIERED_STORAGE("分级存储", "根据文件大小、类型等特征选择不同的存储层级"),
        GEO_LOCATION("地理位置", "根据用户地理位置选择最近的存储服务"),
        CUSTOM("自定义", "自定义存储策略");

        private final String name;
        private final String description;

        StrategyType(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 负载均衡算法枚举
     */
    public enum LoadBalanceAlgorithm {
        ROUND_ROBIN("轮询", "按顺序轮流选择存储服务"),
        WEIGHTED_ROUND_ROBIN("加权轮询", "根据权重选择存储服务"),
        LEAST_CONNECTIONS("最少连接", "选择当前连接数最少的存储服务"),
        RESPONSE_TIME("响应时间", "选择响应时间最短的存储服务"),
        RANDOM("随机", "随机选择存储服务");

        private final String name;
        private final String description;

        LoadBalanceAlgorithm(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 初始化存储策略
     */
    @PostConstruct
    public void init() {
        log.info("初始化可配置存储策略");

        // 加载默认配置
        loadDefaultConfig();

        // 初始化存储服务
        initializeStorageServices();

        // 验证策略配置
        if (!validateStrategy()) {
            log.error("存储策略配置验证失败");
            throw new IllegalStateException("存储策略配置验证失败");
        }

        log.info("可配置存储策略初始化完成，当前策略: {}", currentStrategyType.getName());
    }

    /**
     * 加载默认配置
     */
    private void loadDefaultConfig() {
        strategyConfig.put("strategy.type", "PRIMARY_BACKUP");
        strategyConfig.put("load.balance.algorithm", "ROUND_ROBIN");
        strategyConfig.put("primary.storage.service", "default-local");
        strategyConfig.put("backup.storage.services", Collections.emptyList());
        strategyConfig.put("tiered.storage.rules", Collections.emptyMap());
        strategyConfig.put("health.check.enabled", true);
        strategyConfig.put("health.check.interval", 30000);
        strategyConfig.put("auto.failover.enabled", true);
        strategyConfig.put("auto.failover.threshold", 3);
        strategyConfig.put("retry.enabled", true);
        strategyConfig.put("retry.max.attempts", 3);
        strategyConfig.put("retry.delay", 1000);
        strategyConfig.put("cache.enabled", true);
        strategyConfig.put("cache.ttl", 300000);
        strategyConfig.put("monitoring.enabled", true);
        strategyConfig.put("monitoring.interval", 60000);

        currentStrategyType = StrategyType.PRIMARY_BACKUP;
        loadBalanceAlgorithm = LoadBalanceAlgorithm.ROUND_ROBIN;
    }

    /**
     * 初始化存储服务
     */
    private void initializeStorageServices() {
        // 获取所有存储服务
        Map<String, StorageService> allServices = storageServiceFactory.getAllStorageServices();
        allStorageServices.clear();
        allStorageServices.addAll(allServices.values());

        // 设置主存储服务
        String primaryServiceName = (String) strategyConfig.get("primary.storage.service");
        if (primaryServiceName != null && storageServiceFactory.containsStorageService(primaryServiceName)) {
            primaryStorageService = storageServiceFactory.getStorageService(primaryServiceName);
            log.info("设置主存储服务: {}", primaryServiceName);
        } else if (!allStorageServices.isEmpty()) {
            primaryStorageService = allStorageServices.get(0);
            log.info("使用第一个存储服务作为主存储服务: {}", primaryStorageService.getStorageName());
        }

        // 设置备份存储服务
        backupStorageServices.clear();
        List<String> backupServiceNames = (List<String>) strategyConfig.get("backup.storage.services");
        if (backupServiceNames != null) {
            for (String serviceName : backupServiceNames) {
                if (storageServiceFactory.containsStorageService(serviceName)) {
                    StorageService service = storageServiceFactory.getStorageService(serviceName);
                    if (service != primaryStorageService) {
                        backupStorageServices.add(service);
                        log.info("添加备份存储服务: {}", serviceName);
                    }
                }
            }
        }

        // 初始化权重
        initializeWeights();
    }

    /**
     * 初始化存储服务权重
     */
    private void initializeWeights() {
        storageServiceWeights.clear();
        for (StorageService service : allStorageServices) {
            // 默认权重为100
            storageServiceWeights.put(service, 100L);
        }
    }

    @Override
    public String getStrategyName() {
        return "ConfigurableStorageStrategy";
    }

    @Override
    public String getStrategyDescription() {
        return "可配置存储策略，支持多种策略组合和动态配置";
    }

    @Override
    public StorageService selectStorageService(String fileName, long fileSize, Map<String, Object> metadata) {
        requestCount.incrementAndGet();

        try {
            StorageService selectedService;

            switch (currentStrategyType) {
                case PRIMARY_BACKUP:
                    selectedService = selectByPrimaryBackup(fileName, fileSize, metadata);
                    break;
                case LOAD_BALANCE:
                    selectedService = selectByLoadBalance(fileName, fileSize, metadata);
                    break;
                case TIERED_STORAGE:
                    selectedService = selectByTieredStorage(fileName, fileSize, metadata);
                    break;
                case GEO_LOCATION:
                    selectedService = selectByGeoLocation(fileName, fileSize, metadata);
                    break;
                case CUSTOM:
                    selectedService = selectByCustomRule(fileName, fileSize, metadata);
                    break;
                default:
                    selectedService = selectByPrimaryBackup(fileName, fileSize, metadata);
            }

            if (selectedService != null && selectedService.isAvailable()) {
                successCount.incrementAndGet();
                log.debug("选择存储服务: {} -> {}", fileName, selectedService.getStorageName());
                return selectedService;
            } else {
                failureCount.incrementAndGet();
                log.warn("无法选择可用的存储服务: {}", fileName);
                throw new IllegalStateException("无法选择可用的存储服务");
            }
        } catch (Exception e) {
            failureCount.incrementAndGet();
            log.error("选择存储服务失败: {}", fileName, e);
            throw e;
        }
    }

    @Override
    public StorageService selectStorageServiceForFolder(String folderName, int totalFiles, long totalSize, String structureJson) {
        // 文件夹选择策略可以更复杂，这里使用与文件相同的策略
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "folder");
        metadata.put("totalFiles", totalFiles);
        metadata.put("totalSize", totalSize);
        metadata.put("structureJson", structureJson);

        return selectStorageService(folderName, totalSize, metadata);
    }

    /**
     * 主备模式选择
     */
    private StorageService selectByPrimaryBackup(String fileName, long fileSize, Map<String, Object> metadata) {
        // 首先尝试主存储服务
        if (primaryStorageService != null && primaryStorageService.isAvailable()) {
            return primaryStorageService;
        }

        // 主服务不可用，尝试备份服务
        for (StorageService backupService : backupStorageServices) {
            if (backupService.isAvailable()) {
                log.warn("主存储服务不可用，使用备份服务: {} -> {}", fileName, backupService.getStorageName());
                return backupService;
            }
        }

        // 所有备份服务都不可用，尝试其他可用服务
        for (StorageService service : allStorageServices) {
            if (service.isAvailable() && service != primaryStorageService && !backupStorageServices.contains(service)) {
                log.warn("主备存储服务都不可用，使用其他可用服务: {} -> {}", fileName, service.getStorageName());
                return service;
            }
        }

        return null;
    }

    /**
     * 负载均衡选择
     */
    private StorageService selectByLoadBalance(String fileName, long fileSize, Map<String, Object> metadata) {
        List<StorageService> availableServices = getAvailableStorageServices();
        if (availableServices.isEmpty()) {
            return null;
        }

        switch (loadBalanceAlgorithm) {
            case ROUND_ROBIN:
                return selectByRoundRobin(availableServices);
            case WEIGHTED_ROUND_ROBIN:
                return selectByWeightedRoundRobin(availableServices);
            case LEAST_CONNECTIONS:
                return selectByLeastConnections(availableServices);
            case RESPONSE_TIME:
                return selectByResponseTime(availableServices);
            case RANDOM:
                return selectByRandom(availableServices);
            default:
                return selectByRoundRobin(availableServices);
        }
    }

    /**
     * 轮询选择
     */
    private StorageService selectByRoundRobin(List<StorageService> availableServices) {
        synchronized (this) {
            if (currentRoundRobinIndex >= availableServices.size()) {
                currentRoundRobinIndex = 0;
            }
            StorageService selected = availableServices.get(currentRoundRobinIndex);
            currentRoundRobinIndex++;
            return selected;
        }
    }

    /**
     * 加权轮询选择
     */
    private StorageService selectByWeightedRoundRobin(List<StorageService> availableServices) {
        // 计算总权重
        long totalWeight = availableServices.stream()
            .mapToLong(service -> storageServiceWeights.getOrDefault(service, 100L))
            .sum();

        if (totalWeight <= 0) {
            return selectByRoundRobin(availableServices);
        }

        // 生成随机数
        long random = (long) (Math.random() * totalWeight);
        long current = 0;

        for (StorageService service : availableServices) {
            current += storageServiceWeights.getOrDefault(service, 100L);
            if (random < current) {
                return service;
            }
        }

        return availableServices.get(0);
    }

    /**
     * 最少连接选择
     */
    private StorageService selectByLeastConnections(List<StorageService> availableServices) {
        // TODO: 实现最少连接算法
        // 需要存储服务提供连接数信息
        return selectByRoundRobin(availableServices);
    }

    /**
     * 响应时间选择
     */
    private StorageService selectByResponseTime(List<StorageService> availableServices) {
        // TODO: 实现响应时间算法
        // 需要存储服务提供响应时间信息
        return selectByRoundRobin(availableServices);
    }

    /**
     * 随机选择
     */
    private StorageService selectByRandom(List<StorageService> availableServices) {
        int index = (int) (Math.random() * availableServices.size());
        return availableServices.get(index);
    }

    /**
     * 分级存储选择
     */
    private StorageService selectByTieredStorage(String fileName, long fileSize, Map<String, Object> metadata) {
        // TODO: 实现分级存储策略
        // 根据文件大小、类型等特征选择不同的存储层级
        return selectByPrimaryBackup(fileName, fileSize, metadata);
    }

    /**
     * 地理位置选择
     */
    private StorageService selectByGeoLocation(String fileName, long fileSize, Map<String, Object> metadata) {
        // TODO: 实现地理位置策略
        // 根据用户地理位置选择最近的存储服务
        return selectByPrimaryBackup(fileName, fileSize, metadata);
    }

    /**
     * 自定义规则选择
     */
    private StorageService selectByCustomRule(String fileName, long fileSize, Map<String, Object> metadata) {
        // TODO: 实现自定义规则
        // 允许用户自定义选择规则
        return selectByPrimaryBackup(fileName, fileSize, metadata);
    }

    @Override
    public List<StorageService> getAllAvailableStorageServices() {
        return getAvailableStorageServices();
    }

    @Override
    public StorageService getPrimaryStorageService() {
        return primaryStorageService;
    }

    @Override
    public List<StorageService> getBackupStorageServices() {
        return new ArrayList<>(backupStorageServices);
    }

    @Override
    public void switchPrimaryStorageService(String storageServiceName) {
        StorageService newPrimary = storageServiceFactory.getStorageService(storageServiceName);
        if (newPrimary == null) {
            throw new IllegalArgumentException("存储服务不存在: " + storageServiceName);
        }

        if (!newPrimary.isAvailable()) {
            throw new IllegalStateException("存储服务不可用: " + storageServiceName);
        }

        StorageService oldPrimary = primaryStorageService;
        primaryStorageService = newPrimary;

        // 更新备份服务列表
        if (oldPrimary != null && !backupStorageServices.contains(oldPrimary)) {
            backupStorageServices.add(oldPrimary);
        }
        backupStorageServices.remove(newPrimary);

        // 更新配置
        strategyConfig.put("primary.storage.service", storageServiceName);

        log.info("切换主存储服务: {} -> {}",
            oldPrimary != null ? oldPrimary.getStorageName() : "null",
            newPrimary.getStorageName());
    }

    @Override
    public StorageStats getStrategyStats() {
        StorageStats stats = new StorageStats();
        stats.setStorageName(getStrategyName());
        stats.setStartTime(java.time.LocalDateTime.now().minusDays(1));
        stats.setEndTime(java.time.LocalDateTime.now());

        long totalRequests = requestCount.get();
        long successfulRequests = successCount.get();
        long failedRequests = failureCount.get();

        stats.setSuccessCount(successfulRequests);
        stats.setFailureCount(failedRequests);

//        if (totalRequests > 0) {
//            stats.setSuccessRate((successfulRequests * 100.0) / totalRequests);
//        } else {
//            stats.setSuccessRate(100.0);
//        }

        return stats;
    }

    @Override
    public boolean isStorageServiceAvailable(String storageServiceName) {
        StorageService service = storageServiceFactory.getStorageService(storageServiceName);
        return service != null && service.isAvailable();
    }

    @Override
    public Map<String, Boolean> getStorageHealthStatus() {
        return storageServiceFactory.getStorageHealthStatus();
    }

    @Override
    public void reloadStrategy() {
        log.info("重新加载存储策略");
        initializeStorageServices();
        log.info("存储策略重新加载完成");
    }

    @Override
    public boolean validateStrategy() {
        // 检查主存储服务
        if (primaryStorageService == null) {
            log.error("未设置主存储服务");
            return false;
        }

        // 检查主存储服务是否可用
        if (!primaryStorageService.isAvailable()) {
            log.warn("主存储服务不可用: {}", primaryStorageService.getStorageName());
        }

        // 检查是否有可用的存储服务
        if (getAvailableStorageServices().isEmpty()) {
            log.error("没有可用的存储服务");
            return false;
        }

        // 检查策略配置
        if (currentStrategyType == null) {
            log.error("未设置存储策略类型");
            return false;
        }

        log.info("存储策略验证通过");
        return true;
    }

    @Override
    public Map<String, Object> getStrategyConfig() {
        Map<String, Object> config = new HashMap<>(strategyConfig);
        config.put("currentStrategyType", currentStrategyType.name());
        config.put("loadBalanceAlgorithm", loadBalanceAlgorithm.name());
        config.put("primaryStorageService", primaryStorageService != null ? primaryStorageService.getStorageName() : null);
        config.put("backupStorageServices", backupStorageServices.stream()
            .map(StorageService::getStorageName)
            .toList());
        config.put("availableStorageServices", getAvailableStorageServices().stream()
            .map(StorageService::getStorageName)
            .toList());
        config.put("requestCount", requestCount.get());
        config.put("successCount", successCount.get());
        config.put("failureCount", failureCount.get());
        return config;
    }

    /**
     * 获取可用的存储服务列表
     */
    private List<StorageService> getAvailableStorageServices() {
        List<StorageService> availableServices = new ArrayList<>();
        for (StorageService service : allStorageServices) {
            if (service.isAvailable()) {
                availableServices.add(service);
            }
        }
        return availableServices;
    }

    /**
     * 更新策略配置
     */
    public void updateStrategyConfig(Map<String, Object> newConfig) {
        if (newConfig == null || newConfig.isEmpty()) {
            return;
        }

        strategyConfig.putAll(newConfig);

        // 更新策略类型
        if (newConfig.containsKey("strategy.type")) {
            String typeStr = (String) newConfig.get("strategy.type");
            try {
                currentStrategyType = StrategyType.valueOf(typeStr);
                log.info("更新存储策略类型: {}", currentStrategyType.getName());
            } catch (IllegalArgumentException e) {
                log.error("无效的存储策略类型: {}", typeStr);
            }
        }

        // 更新负载均衡算法
        if (newConfig.containsKey("load.balance.algorithm")) {
            String algorithmStr = (String) newConfig.get("load.balance.algorithm");
            try {
                loadBalanceAlgorithm = LoadBalanceAlgorithm.valueOf(algorithmStr);
                log.info("更新负载均衡算法: {}", loadBalanceAlgorithm.getName());
            } catch (IllegalArgumentException e) {
                log.error("无效的负载均衡算法: {}", algorithmStr);
            }
        }

        // 重新初始化存储服务
        initializeStorageServices();

        log.info("更新存储策略配置完成");
    }

    /**
     * 设置存储服务权重
     */
    public void setStorageServiceWeight(String storageServiceName, long weight) {
        StorageService service = storageServiceFactory.getStorageService(storageServiceName);
        if (service == null) {
            throw new IllegalArgumentException("存储服务不存在: " + storageServiceName);
        }

        if (weight <= 0) {
            throw new IllegalArgumentException("权重必须大于0");
        }

        storageServiceWeights.put(service, weight);
        log.info("设置存储服务权重: {} -> {}", storageServiceName, weight);
    }

    /**
     * 获取当前策略类型
     */
    public StrategyType getCurrentStrategyType() {
        return currentStrategyType;
    }

    /**
     * 获取当前负载均衡算法
     */
    public LoadBalanceAlgorithm getLoadBalanceAlgorithm() {
        return loadBalanceAlgorithm;
    }
}