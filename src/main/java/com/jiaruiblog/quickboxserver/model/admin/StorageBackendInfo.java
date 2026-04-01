package com.jiaruiblog.quickboxserver.model.admin;

import com.jiaruiblog.quickboxserver.storage.model.StorageConfig;
import lombok.Data;

@Data
public class StorageBackendInfo {
    private String name;
    private com.jiaruiblog.quickboxserver.storage.model.StorageType type;
    private StorageConfig config;
    private boolean available;
    private String healthStatus;
    private String errorMessage;
}
