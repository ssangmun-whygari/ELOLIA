package com.finalProject.payment;

import java.util.Base64;

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
public class TossPaymentStrategy implements PaymentStrategy {
	@Value("${payment.toss.secretKey}")
	private String secretKey;

	private final RestTemplate restTemplate = new RestTemplate();
	private final Gson gson = new Gson();
	
	@Override
	public PaymentType getType() {
		return PaymentType.TOSS;
	}
	
	@Override
	public PaymentResult ready(PaymentRequest request) {
		return PaymentResult.success("토스페이먼츠는 ready 과정 필요 없음.");
	}
	
	@Override
	public PaymentResult approve(PaymentRequest request) {
		String requestUrl = "https://api.tosspayments.com/v1/payments/confirm";

        HttpHeaders headers = new HttpHeaders();
        String encodedKey = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());
        headers.set("Authorization", "Basic " + encodedKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        JsonObject body = new JsonObject();
        body.addProperty("paymentKey", request.getPaymentKey());
        body.addProperty("amount", request.getAmount());
        body.addProperty("orderId", request.getOrderId());
		
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(
				requestUrl,
				new HttpEntity<>(gson.toJson(body), headers),
				String.class
			);
			return PaymentResult.builder()
				.success(true)
				.statusCode(response.getStatusCodeValue())
				.build();
		} catch (HttpStatusCodeException e) {
			System.out.println("토스페이먼츠 결제 승인 실패 - HTTP " + e.getRawStatusCode());
			e.printStackTrace();
			return PaymentResult.fail(e.getRawStatusCode(), e.getResponseBodyAsString());
		}
	}

	@Override
	public PaymentResult cancel(PaymentRequest request) {
		String encodedSecretKey = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());
		String requestUrl = String.format("https://api.tosspayments.com/v1/payments/%s/cancel", request.getPaymentKey()); 
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", String.format("Basic %s", encodedSecretKey));
		headers.setContentType(MediaType.APPLICATION_JSON);
		
        JsonObject body = new JsonObject();
        body.addProperty("cancelReason", request.getCancelReason());
        body.addProperty("cancelAmount", request.getAmount());
        
		HttpEntity<String> requestEntity = new HttpEntity<>(gson.toJson(body), headers);		
        try {
			ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, requestEntity, String.class);
			return PaymentResult.builder()
				.success(true)
				.statusCode(response.getStatusCodeValue())
				.rawResponse(response.getBody())
				.build();
        } catch (HttpStatusCodeException e) {
        	System.out.println("토스페이먼츠 결제취소 승인 실패 - HTTP " + e.getRawStatusCode());
			e.printStackTrace();
			return PaymentResult.fail(e.getRawStatusCode(), e.getResponseBodyAsString());
        }
	}
}
