package com.finalProject.payment;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class PaymentResult {
	private boolean success;
	private int statusCode;
	private String message;
	private String rawResponse;
	
	public static PaymentResult success(String message) {
		return PaymentResult.builder()
			.success(true)
			.statusCode(200)
			.message(message)
			.build();
	}
	
	public static PaymentResult fail(int statusCode, String message) {
		return PaymentResult.builder()
				.success(false)
				.statusCode(statusCode)
				.message(message)
				.build();
	}
}
