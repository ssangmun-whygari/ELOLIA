package com.finalProject.payment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Component
public class NaverPaymentStrategy implements PaymentStrategy {
	@Value("${payment.naver.clientId}")
	private String clientId;
	
	@Value("${payment.naver.clientSecret}")
	private String clientSecret;

	@Value("${payment.naver.chainId}")
	private String chainId;

	private final RestTemplate restTemplate = new RestTemplate();
	private final Gson gson = new Gson();
	
	@Override
	public PaymentType getType() {
		return PaymentType.NAVER;
	}
	
	@Override
	public PaymentResult ready(PaymentRequest request) {
		return PaymentResult.success("네이버페이는 ready 과정 필요 없음.");
	}
	
	
	@Override
	public PaymentResult approve(PaymentRequest request) {
		String requestUrl = "https://dev-pay.paygate.naver.com/naverpay-partner/naverpay/payments/v2.2/apply/payment";
				
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.set("X-Naver-Client-Id", this.clientId);
		headers.set("X-Naver-Client-Secret", this.clientSecret);
		headers.set("X-NaverPay-Chain-Id", this.chainId);
		headers.set("X-NaverPay-Idempotency-Key", UUID.randomUUID().toString());
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("paymentId", request.getPaymentKey());
		
		HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
		
		try {
			ResponseEntity<Map> response = restTemplate.postForEntity(requestUrl, entity, Map.class);
			Map<String, String> body = response.getBody();
			boolean isSuccess = "Success".equals(body.get("code"));
			return PaymentResult.builder()
					.success(isSuccess)
					.statusCode(response.getStatusCodeValue())
					.build();
		} catch (HttpStatusCodeException e) {
			System.out.println("네이버페이 결제 승인 실패 - HTTP " + e.getRawStatusCode());
			System.out.println("네이버페이 응답 : " + e.getResponseBodyAsString());
			e.printStackTrace();
			return PaymentResult.fail(e.getRawStatusCode(), e.getResponseBodyAsString());
		}
	}

	@Override
	public PaymentResult cancel(PaymentRequest request) {
		String requestUrl = "https://dev-pay.paygate.naver.com/naverpay-partner/naverpay/payments/v1/cancel";
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Naver-Client-Id", this.clientId);
		headers.set("X-Naver-Client-Secret", this.clientSecret);
		headers.set("X-NaverPay-Chain-Id", this.chainId);
		headers.set("X-NaverPay-Idempotency-Key", UUID.randomUUID().toString());
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("paymentId", request.getPaymentKey());
		form.add("cancelAmount", String.valueOf(request.getAmount()));
		form.add("cancelReason", request.getCancelReason());
		form.add("taxScopeAmount", String.valueOf(request.getAmount()));
		form.add("taxExScopeAmount", "0");
		form.add("cancelRequester", "2");
		
		HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, entity, String.class);
			return PaymentResult.builder()
				.success(true)
				.statusCode(response.getStatusCodeValue())
				.rawResponse(response.getBody())
				.build();
		} catch (HttpStatusCodeException e) {
			System.out.println("네이버페이 결제취소 승인 실패 - HTTP " + e.getRawStatusCode());
			e.printStackTrace();
			return PaymentResult.fail(e.getRawStatusCode(), e.getResponseBodyAsString());
		}
	}
}
