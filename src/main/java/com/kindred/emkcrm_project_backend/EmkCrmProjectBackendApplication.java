package com.kindred.emkcrm_project_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EmkCrmProjectBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmkCrmProjectBackendApplication.class, args);
    }
}
