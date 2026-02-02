package com.finalProject.payment;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class PaymentRequest {
	private String orderId;
	private String paymentKey; // 토스: paymentKey, 카카오: tid, 네이버: paymentId
	private int amount;
	private String pgToken;         // 카카오 approve용
	private String orderName; // 예 : 목걸이 외 x건
	private String cancelReason; // 취소 요청용
}