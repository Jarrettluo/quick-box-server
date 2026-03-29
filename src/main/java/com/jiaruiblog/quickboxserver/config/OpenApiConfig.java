package com.jiaruiblog.quickboxserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI quickBoxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuickBox API")
                        .description("临时文件共享服务 API，提供文件上传、下载和管理功能。文件在7天后自动删除，或下载后立即删除。")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("QuickBox Team")
                                .url("https://github.com/jiaruiblog/quick-box-server"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
