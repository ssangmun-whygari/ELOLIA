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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Component
public class KakaoPaymentStrategy implements PaymentStrategy {
	@Value("${payment.kakao.secretKey}")
	private String secretKey;

	private final RestTemplate restTemplate = new RestTemplate();
	private final Gson gson = new Gson();
	
	@Override
	public PaymentType getType() {
		return PaymentType.KAKAO;
	}
	
	@Override
	public PaymentResult ready(PaymentRequest request) {
		String requestUrl = "https://open-api.kakaopay.com/online/v1/payment/ready";
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "SECRET_KEY " + this.secretKey);
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		JsonObject root = new JsonObject();
		root.addProperty("cid", "TC0ONETIME");
		root.addProperty("partner_order_id", "partner_order_id");
		root.addProperty("partner_user_id", "partner_user_id");
		root.addProperty("item_name", request.getOrderName());
		root.addProperty("quantity", 1);
		root.addProperty("total_amount", request.getAmount());
		root.addProperty("vat_amount", 0);
		root.addProperty("tax_free_amount", 0);
		root.addProperty("approval_url", "http://localhost:8080/kakaopay_payRequest");
		root.addProperty("fail_url", "http://localhost:8080/pages/order/orderFail");
		root.addProperty("cancel_url", "http://localhost:8080/pages/order/orderFail");
		String jsonBody = gson.toJson(root);
		
		HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers); 
		Map<String, String> resultMap = new HashMap<>();
		
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, requestEntity, String.class);
			// Map<String, Object> responseBody = gson.fromJson(response.getBody(), Map.class);
			
			return PaymentResult.builder()
					.success(true)
					.statusCode(response.getStatusCodeValue())
					.rawResponse(response.getBody())
					.build();
		} catch (HttpStatusCodeException e) {
			return PaymentResult.fail(e.getRawStatusCode(), e.getResponseBodyAsString());
		}
	}
	
	
	@Override
	public PaymentResult approve(PaymentRequest request) {
		String requestUrl = "https://open-api.kakaopay.com/online/v1/payment/approve";
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "SECRET_KEY " + this.secretKey);
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		JsonObject root = new JsonObject();
		root.addProperty("cid", "TC0ONETIME");
		root.addProperty("partner_order_id", "partner_order_id");
		root.addProperty("partner_user_id", "partner_user_id");
		root.addProperty("tid", request.getPaymentKey());
		root.addProperty("pg_token", request.getPgToken());
		String jsonBody = gson.toJson(root);
		
		HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers); 
		Map<String, String> resultMap = new HashMap<>();
		
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, requestEntity, String.class);
			return PaymentResult.builder()
					.success(true)
					.statusCode(response.getStatusCodeValue())
					.build();
		} catch (HttpStatusCodeException e) {
			System.out.println("카카오페이 결제승인 실패 - HTTP " + e.getRawStatusCode());
			e.printStackTrace();
			return PaymentResult.fail(e.getRawStatusCode(), e.getResponseBodyAsString());
		}
	}

	@Override
	public PaymentResult cancel(PaymentRequest request) {
		String requestUrl = "https://open-api.kakaopay.com/online/v1/payment/cancel";
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "SECRET_KEY " + this.secretKey);
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		JsonObject root = new JsonObject();
		root.addProperty("cid", "TC0ONETIME");
		root.addProperty("tid", request.getPaymentKey());
		root.addProperty("cancel_amount", request.getAmount());
		root.addProperty("cancel_tax_free_amount", 0);
		String jsonBody = gson.toJson(root);
		
		HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);		
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, requestEntity, String.class);
			return PaymentResult.builder()
				.success(true)
				.statusCode(response.getStatusCodeValue())
				.rawResponse(response.getBody())
				.build();
		} catch (HttpStatusCodeException e) {
			System.out.println("카카오페이 결제취소 승인 실패 - HTTP " + e.getRawStatusCode());
			e.printStackTrace();
			return PaymentResult.fail(e.getRawStatusCode(), e.getResponseBodyAsString());
		}
	}
}
