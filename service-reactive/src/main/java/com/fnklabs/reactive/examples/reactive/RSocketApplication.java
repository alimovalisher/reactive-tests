package com.fnklabs.reactive.examples.reactive;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RSocketApplication {
    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(RSocketApplication.class);
        springApplication.run(args);
    }
}

