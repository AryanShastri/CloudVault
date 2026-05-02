package com.cloudvault.storage_engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class StorageEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(StorageEngineApplication.class, args);
	}

}
