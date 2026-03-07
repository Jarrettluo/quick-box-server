package com.jiaruiblog.quickboxserver.storage.strategy;

import com.jiaruiblog.quickboxserver.storage.StorageService;
import com.jiaruiblog.quickboxserver.storage.model.StorageStats;

import java.util.List;
import java.util.Map;

/**
 * 存储策略接口
 * 定义存储服务的路由和选择策略
 */
public interface StorageStrategy {

    /**
     * 获取存储服务名称
     */
    String getStrategyName();

    /**
     * 获取存储策略描述
     */
    String getStrategyDescription();

    /**
     * 根据文件信息选择存储服务
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param metadata 元数据
     * @return 选择的存储服务
     */
    StorageService selectStorageService(String fileName, long fileSize, Map<String, Object> metadata);

    /**
     * 根据文件夹信息选择存储服务
     * @param folderName 文件夹名
     * @param totalFiles 总文件数
     * @param totalSize 总大小
     * @param structureJson 目录结构JSON
     * @return 选择的存储服务
     */
    StorageService selectStorageServiceForFolder(String folderName, int totalFiles, long totalSize, String structureJson);

    /**
     * 获取所有可用的存储服务
     */
    List<StorageService> getAllAvailableStorageServices();

    /**
     * 获取主存储服务
     */
    StorageService getPrimaryStorageService();

    /**
     * 获取备份存储服务列表
     */
    List<StorageService> getBackupStorageServices();

    /**
     * 切换主存储服务
     * @param storageServiceName 存储服务名称
     */
    void switchPrimaryStorageService(String storageServiceName);

    /**
     * 获取存储策略统计信息
     */
    StorageStats getStrategyStats();

    /**
     * 检查存储服务是否可用
     * @param storageServiceName 存储服务名称
     */
    boolean isStorageServiceAvailable(String storageServiceName);

    /**
     * 获取存储服务健康状态
     */
    Map<String, Boolean> getStorageHealthStatus();

    /**
     * 重新加载存储策略配置
     */
    void reloadStrategy();

    /**
     * 验证存储策略配置
     */
    boolean validateStrategy();

    /**
     * 获取策略配置信息
     */
    Map<String, Object> getStrategyConfig();
}