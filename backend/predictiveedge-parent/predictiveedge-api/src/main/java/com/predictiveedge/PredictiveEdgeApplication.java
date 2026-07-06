package com.predictiveedge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = "com.predictiveedge")
@EnableJpaAuditing
public class PredictiveEdgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PredictiveEdgeApplication.class, args);
    }
}
