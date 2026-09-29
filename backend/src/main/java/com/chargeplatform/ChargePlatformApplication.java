package com.chargeplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChargePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChargePlatformApplication.class, args);
    }
}
