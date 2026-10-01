package com.stylenest.stylenest_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling powers RentalBookingExpiryScheduler only (sweeps
// abandoned PENDING_PAYMENT rental holds) -- nothing else in the app uses
// @Scheduled. Safe to remove alongside the rental package after Navratri.
@SpringBootApplication
@EnableScheduling
public class StyleNestBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(StyleNestBackendApplication.class, args);
	}

}