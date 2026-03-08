package com.jiaruiblog.quickboxserver.model.admin;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
    public class StrategyRequest {
        private String strategyType;
        private String primaryStorageService;
        private List<String> backupStorageServices;
        private String loadBalanceAlgorithm;
        private Map<String, Object> additionalConfig;
    }