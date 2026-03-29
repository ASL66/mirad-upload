package com.example.mirad;

import com.example.mirad.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class MiradUploadApplication {
    private final AppProperties appProperties;

    public MiradUploadApplication(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public static void main(String[] args) {
        SpringApplication.run(MiradUploadApplication.class, args);
    }

    @PostConstruct
    public void initializeDirectories() {
        appProperties.ensureDirectories();
    }
}
