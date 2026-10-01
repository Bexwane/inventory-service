package com.enterprise.inventory;

import com.enterprise.inventory.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Enterprise Inventory Service.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class InventoryServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryServiceApplication.class, args);
	}

}
