package com.contestradar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ContestRadarApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContestRadarApplication.class, args);
    }
}
