package com.jiaruiblog.quickboxserver.model.request;

public record FileCheckRequest(
    String fileMd5,
    String filename,
    Long fileSize
) {}