package org.aiincubator.ilmai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class IlmaiBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(IlmaiBackendApplication.class, args);
    }

}
