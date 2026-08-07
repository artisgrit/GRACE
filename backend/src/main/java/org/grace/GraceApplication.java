package org.grace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GraceApplication {
    public static void main(String[] args) {
        SpringApplication.run(GraceApplication.class, args);
    }
}
