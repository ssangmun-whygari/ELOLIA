package com.finalProject.payment;

public interface PaymentStrategy {
	// 결제 준비 (카카오페이만 필요)
	PaymentResult ready(PaymentRequest request);

	// 결제 승인
	PaymentResult approve(PaymentRequest request);

    // 결제 취소
    PaymentResult cancel(PaymentRequest request);

    // 지원하는 결제 타입
    PaymentType getType();
}
