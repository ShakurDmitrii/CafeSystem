package com.shakur.cafehelp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CafehelpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CafehelpApplication.class, args);
    }

}
