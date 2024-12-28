package com.fnklabs.reactive.examples.flux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FluxApplication {
    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(FluxApplication.class);
        springApplication.setWebApplicationType(WebApplicationType.REACTIVE);
        springApplication.run(args);
    }
}
