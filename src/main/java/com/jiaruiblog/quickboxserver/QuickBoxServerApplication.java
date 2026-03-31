package com.jiaruiblog.quickboxserver;

import com.jiaruiblog.quickboxserver.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class QuickBoxServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickBoxServerApplication.class, args);
    }

}
