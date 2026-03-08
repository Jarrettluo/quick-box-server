package com.jiaruiblog.quickboxserver.model.admin;

import lombok.Data;

@Data
public class WeightRequest {
    private String storageServiceName;
    private long weight;
}