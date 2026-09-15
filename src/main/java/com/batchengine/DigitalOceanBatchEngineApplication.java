package com.batchengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class DigitalOceanBatchEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(DigitalOceanBatchEngineApplication.class, args);
	}

}
