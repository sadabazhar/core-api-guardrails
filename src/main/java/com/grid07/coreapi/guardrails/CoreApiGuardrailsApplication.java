package com.grid07.coreapi.guardrails;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class CoreApiGuardrailsApplication {

	public static void main(String[] args) {

        // Set timezone at JVM level (Avoid time zone problem)
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));

        SpringApplication.run(CoreApiGuardrailsApplication.class, args);
	}

}
