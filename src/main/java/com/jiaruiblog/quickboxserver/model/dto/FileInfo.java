package com.jiaruiblog.quickboxserver.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileInfo {
    private String filename;
    private long size;
    private long lastModified;
    private String downloadUrl;
}