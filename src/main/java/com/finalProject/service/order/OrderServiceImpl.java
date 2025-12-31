package com.finalProject.service.order;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.finalProject.model.order.CancelOrderRequestDTO;
import com.finalProject.model.order.OrderMemberDTO;
import com.finalProject.model.order.OrderProductDTO;
import com.finalProject.model.order.OrderProductsDTO;
import com.finalProject.model.order.OrderRequestDTO;
import com.finalProject.model.order.PaymentRequestDTO;
import com.finalProject.model.order.ProductDiscountCalculatedDTO;
import com.finalProject.model.order.ProductDiscountDTO;
import com.finalProject.persistence.order.OrderDAO;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

@Service
public class OrderServiceImpl implements OrderService {
	@Inject
	private OrderDAO orderDAO;
	
	private static Gson gson = new Gson();
	private static RestTemplate restTemplate = new RestTemplate();
	
	@Value("${payment.toss.secretKey}")
	private String tossSecretKey;
	@Value("${payment.naver.clientId}")
	private String naverClientId;
	@Value("${payment.naver.clientSecret}")
	private String naverClientSecret;
	@Value("${payment.naverPay.chainId}")
	private String naverChainId;
	@Value("${payment.kakao.secretKey}")
	private String kakaoSecretKey;

	@Override
	public void deleteOrder(String orderId) {
		orderDAO.deleteOrder(orderId);
	}

	@Override
	public Map<String, Object> makeOrder(PaymentRequestDTO request, boolean isMember, HttpSession session)
			throws Exception {
		Map<String, Object> result = new HashMap<>();
		System.out.println("orders 테이블 행 삽입 전 회원/비회원 확인. 회원? : " + isMember);
		String orderId = orderDAO.makeOrder(request, isMember);
		Integer expectedTotalPrice = this.setExpectedPrices(request, orderId, isMember, session);
		result.put("orderId", orderId);
		result.put("expectedTotalPrice", expectedTotalPrice);
		return result;
	}

	// 총 예상결제금액과 상품별 금액, 상품별 포인트를 설정한다.
	private int setExpectedPrices(PaymentRequestDTO request, String orderId, boolean isMember, HttpSession session)
			throws Exception {
		int expectedTotalPrice = 0;
		List<ProductDiscountDTO> discountInfo = orderDAO.getDiscountInfoByProduct(orderId, session);
		List<ProductDiscountCalculatedDTO> productDiscountCalculated = new ArrayList<>();

		for (int i = 0; i < request.getProductsInfo().size(); i++) {
			productDiscountCalculated.add(new ProductDiscountCalculatedDTO().builder()
					.orderproduct_no(discountInfo.get(i).getOrderproduct_no()).build()); // orderProduct_no 설정
			ProductDiscountDTO p = discountInfo.get(i);
			System.out.println("이 아이템의 모든 할인 전 가격 : " + p.getProductPrice() * p.getOrderCount());
			System.out.println("상품 자체 할인 적용후 가격 : " + p.getProductPrice() * p.getOrderCount() * (1 - p.getDiscountByItem()) );
			System.out.println("아이템의 % 할인율(쿠폰) : " + p.getMultipliedDiscountByCoupon());
			System.out.println("아이템의 % 할인율(멤버 레벨) : " + p.getMultipliedDiscountByMemberLevel());
			System.out.println("아이템의 % 할인율(상품 자체 할인) : " + p.getDiscountByItem());
			
			// 최소 할인 단위는 10원이다.
			int CpouponPercentDiscountOfItem = ((int) (p.getProductPrice() * p.getOrderCount() * p.getMultipliedDiscountByCoupon())) / 10 * 10;
			int MemberPercentDiscountOfItem = ((int) (p.getProductPrice() * p.getOrderCount() * p.getMultipliedDiscountByMemberLevel())) / 10 * 10;
			int ItemPercentDiscountOfItem = ((int) (p.getProductPrice() * p.getOrderCount() * p.getDiscountByItem())) / 10 * 10;
			
			expectedTotalPrice += (p.getProductPrice() * p.getOrderCount());
			expectedTotalPrice -= (CpouponPercentDiscountOfItem + MemberPercentDiscountOfItem + ItemPercentDiscountOfItem);
		}
		// 절대값 할인 (쿠폰 할인 + 포인트 할인)
		expectedTotalPrice -= (discountInfo.get(0).getSumDiscountByCoupon()
				+ discountInfo.get(0).getSumDiscountByPoint());
		System.out.println("절대값 할인 : "
				+ (discountInfo.get(0).getSumDiscountByCoupon() + discountInfo.get(0).getSumDiscountByPoint()));
		// 이 시점에서 expectedTotalPrice는 할인이 모두 적용된 값 (택배값 미포함)

		// 가중치 계산
		int denominator = 0;
		// 분모는 % 할인만 포함하므로 절대값 할인을 다시 더해준다.
		denominator = expectedTotalPrice + (discountInfo.get(0).getSumDiscountByCoupon() + discountInfo.get(0).getSumDiscountByPoint());
		
		System.out.println("가중치 계산을 위한 분모 : " + denominator);
		Iterator<ProductDiscountCalculatedDTO> iter = productDiscountCalculated.iterator();
		for (ProductDiscountDTO p : discountInfo) {
			int numerator = 0;
			numerator += p.getProductPrice() * p.getOrderCount();
			// 최소 할인 단위는 10원이다.
			int CpouponPercentDiscountOfItem = ((int) (p.getProductPrice() * p.getOrderCount() * p.getMultipliedDiscountByCoupon())) / 10 * 10;
			int MemberPercentDiscountOfItem = ((int) (p.getProductPrice() * p.getOrderCount() * p.getMultipliedDiscountByMemberLevel())) / 10 * 10;
			int ItemPercentDiscountOfItem = ((int) (p.getProductPrice() * p.getOrderCount() * p.getDiscountByItem())) / 10 * 10;
			numerator -= (CpouponPercentDiscountOfItem + MemberPercentDiscountOfItem + ItemPercentDiscountOfItem);
			double weight = (double) numerator / denominator;
			System.out.println("분자 / 분모 = " + (p.getProductPrice() * p.getOrderCount() * (1 - p.getDiscountByItem())) + " / " + denominator + " = " + weight);
			iter.next().setWeight(weight); // 가중치 설정
		}

		for (int i = 0; i < request.getProductsInfo().size(); i++) {
			if (i == 0) { // 리스트의 첫번째 요소이면
				int remainedPoint = discountInfo.get(i).getSumDiscountByPoint();
				int allocatedPoint = (int) Math.round(productDiscountCalculated.get(i).getWeight() * remainedPoint);
				productDiscountCalculated.get(i).setRemainedPoint(remainedPoint - allocatedPoint);
				productDiscountCalculated.get(i).setRefundPoint(allocatedPoint);
				System.out.println("remainedPoint : " + remainedPoint);
				System.out.println("allocatedPoint : " + allocatedPoint);
			} else if (i != request.getProductsInfo().size() - 1) {
				int remainedPoint = productDiscountCalculated.get(i - 1).getRemainedPoint();
				int allocatedPoint = (int) Math.round(
						productDiscountCalculated.get(i).getWeight() * discountInfo.get(i).getSumDiscountByPoint());
				productDiscountCalculated.get(i).setRemainedPoint(remainedPoint - allocatedPoint);
				productDiscountCalculated.get(i).setRefundPoint(allocatedPoint);
				System.out.println("remainedPoint : " + remainedPoint);
				System.out.println("allocatedPoint : " + allocatedPoint);
			} else { // 리스트의 마지막 요소이면
				int allocatedPoint = productDiscountCalculated.get(i - 1).getRemainedPoint();
				productDiscountCalculated.get(i).setRemainedPoint(0);
				productDiscountCalculated.get(i).setRefundPoint(allocatedPoint);
				System.out.println("allocatedPoint : " + allocatedPoint);
			}
		} // 할당된 포인트의 합이 무조건 discountInfo.get(i).getSumDiscountByPoint()과 같아지게 계산됨

		for (int i = 0; i < request.getProductsInfo().size(); i++) {
			if (i == 0) { // 리스트의 첫번째 요소이면
				int remainedPrice = expectedTotalPrice;
				int allocatedPrice = 
					((int) Math.round(productDiscountCalculated.get(i).getWeight() * expectedTotalPrice))
					 / 10 * 10; // 최소 10원 단위
				productDiscountCalculated.get(i).setRemainedPrice(remainedPrice - allocatedPrice);
				productDiscountCalculated.get(i).setRefundPrice(allocatedPrice);
				System.out.println("remainedPrice : " + remainedPrice);
				System.out.println("allocatedPrice : " + allocatedPrice);
			} else if (i != request.getProductsInfo().size() - 1) {
				int remainedPrice = productDiscountCalculated.get(i - 1).getRemainedPrice();
				int allocatedPrice = 
					((int) Math.round(productDiscountCalculated.get(i).getWeight() * expectedTotalPrice))
					/ 10 * 10; // 최소 10원 단위
				productDiscountCalculated.get(i).setRemainedPrice(remainedPrice - allocatedPrice);
				productDiscountCalculated.get(i).setRefundPrice(allocatedPrice);
				System.out.println("remainedPrice : " + remainedPrice);
				System.out.println("allocatedPrice : " + allocatedPrice);
			} else { // 리스트의 마지막 요소이면
				int allocatedPrice = productDiscountCalculated.get(i - 1).getRemainedPrice();
				productDiscountCalculated.get(i).setRefundPrice(allocatedPrice);
				productDiscountCalculated.get(i).setRemainedPrice(0);
				System.out.println("allocatedPrice : " + allocatedPrice);
			}
		}

		System.out.println("productDiscountCalculated : " + productDiscountCalculated);
		orderDAO.updateExpectedTotalPriceWithDeliveryCost(orderId, expectedTotalPrice); // 배송비를 더해서 DB에 저장
		orderDAO.updateRefundPriceByProduct(productDiscountCalculated);
		return orderDAO.getExpectedTotalPrice(orderId);
	}

	@Override
	public void makeGuest(PaymentRequestDTO request, String orderId) {
		if (orderDAO.makeGuest(request, orderId) != 1) {
			throw new DataAccessException("비회원 정보 생성 실패") {
			};
		}
	}

	@Override
	@Transactional(rollbackFor = { Exception.class })
	public void makePayment(String orderId, Integer amount, String payModule, String method, HttpSession session)
			throws Exception {
		// NOTE : 이 트랜잭션에서 예외가 발생해도 이미 생성된 orders의 테이블의 행은 삭제되지 않음
		// 그러므로 컨트롤러의 catch 블록에서 orders 테이블의 행을 삭제함
		System.out.println("makePayment 함수 실행, method 매개변수 : " + method);

		boolean isMember = session.getAttribute("loginMember") == null ? false : true;

		if (isMember == true) {
			// 유저정보 업데이트 : 쿠폰 사용, 포인트 적립, 회원등급 수정
			String couponCode = (String) session.getAttribute("couponCodeUsed");
			System.out.println("###############makePayment##############");
			System.out.println("orderId : " + orderId);
			System.out.println("couponCode : " + couponCode);
			
			if (couponCode == null) {
				couponCode = "";
			}
			// 쿠폰 사용
			if (!("".equals(couponCode))) {
				if (orderDAO.useCoupon(orderId, couponCode) != 1) {
					throw new DataAccessException("쿠폰 사용 실패") {};
				};
			};

			// 포인트 적립
			if (orderDAO.updatePoint(orderId) != true) {
				throw new DataAccessException("포인트 적립 실패") {
				};
			}
			;
			// 회원등급 수정
			if (orderDAO.updateUserLevel(orderId) != true) {
				throw new DataAccessException("회원등급 수정 실패") {
				};
			}
			;
		}
		
		// 결제 내역 삽입
		if (orderDAO.insertPaymentInfo(orderId, amount, payModule, method) != true) {
			throw new DataAccessException("결제 정보 생성 실패") {}; 
		}
		
		orderDAO.updateOrderStatus(method, orderId);

		// TODO : 장바구니에서 결제한 물품 삭제
	}
	
	// 카트에서 결제한 상품 지우기(회원)
	@Override
	public void deletePaidProductsFromCart(String memberId) {
		orderDAO.deletePaidProductsFromCart(memberId);
	}
		
	@Override
	@Transactional(rollbackFor = { Exception.class })
	public void setPaymentModuleKey(String orderId, String key) throws Exception {
		if (orderDAO.setPaymentModuleKey(orderId, key) != 1) {
			throw new DataAccessException("DB 조작 실패") {
			};
		}
	}

	@Override
	public String getPaymentModuleKey(String orderId) {
		return orderDAO.getPaymentModuleKey(orderId);
	}

	@Override
	public int getExpectedTotalPrice(String orderId) {
		return orderDAO.getExpectedTotalPrice(orderId);
	}

	@Override
	public List<OrderProductDTO> getProductInfo(List<OrderRequestDTO> requestsInfo) {

		return orderDAO.selectProductInfo(requestsInfo);
	}

	@Override
	public OrderMemberDTO getMemberInfo(String memberId) {

		return orderDAO.selectMemberInfo(memberId);
	}

	@Override
	public Map<String, String> requestApproval(String paymentKey, int amount, String orderId) {
		String base64SecretKey = Base64.getEncoder().encodeToString((this.tossSecretKey + ":").getBytes());
		String requestUrl = "https://api.tosspayments.com/v1/payments/confirm";
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Basic " + base64SecretKey);
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		JsonObject root = new JsonObject();
		root.addProperty("paymentKey", paymentKey);
		root.addProperty("amount", amount);
		root.addProperty("orderId", orderId);
		String jsonBody = gson.toJson(root);
		
		HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);
		
		Map<String, String> resultMap = new HashMap<>();
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, requestEntity, String.class);
			resultMap.put("status", response.getStatusCodeValue() + "");
			resultMap.put("response", response.getBody());
		} catch (HttpStatusCodeException e) {
		    resultMap.put("status", e.getRawStatusCode() + "");
		    resultMap.put("response", e.getResponseBodyAsString());
		    System.out.println("결제 승인 실패 - HTTP " + e.getRawStatusCode());
		    e.printStackTrace();
		    throw e;
		} catch (Exception e) {
			resultMap.put("response", null);
		    System.out.println("결제 승인 실패 - 기타 예외");
		    e.printStackTrace();
		    throw new RuntimeException("결제 승인 실패", e);
		}
		
		// TODO : 무통장입금으로 결제방법을 지정하면 응답에
		// "virtualAccount": {
		// "accountNumber": "X5909014050733",
		// "accountType": "일반",
		// "bankCode": "06",
		// "customerName": "dsfsdf",
		// "dueDate": "2024-10-26T15:59:03+09:00",
		// "expired": false,
		// "settlementStatus": "INCOMPLETED",
		// "refundStatus": "NONE",
		// "refundReceiveAccount": null
		// },
		// 이런 데이터가 포함된다. bankCode마다 은행종류 잇으니까 은행 종류하고 입금할 가상계좌번호 결제 완료 뷰페이지에 표시해야 함
		return resultMap;
	}

	@Override
	public Map<String, String> requestApprovalNaverpayPayment(String paymentId) {
		String requestUrl = "https://dev-pay.paygate.naver.com/naverpay-partner/naverpay/payments/v2.2/apply/payment";
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		headers.set("X-Naver-Client-Id", this.naverClientId);
		headers.set("X-Naver-Client-Secret", this.naverClientSecret);
		headers.set("X-NaverPay-Chain-Id", this.naverChainId);
		headers.set("X-NaverPay-Idempotency-Key", UUID.randomUUID().toString());
		
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("paymentId", paymentId);
		
		HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
		
		Map<String, String> body = null;
		try {
			ResponseEntity<Map> response = this.restTemplate.postForEntity(requestUrl, entity, Map.class);
			body = response.getBody();
		} catch (HttpStatusCodeException e) {
		    System.out.println("네이버페이 결제 승인 실패 - HTTP " + e.getRawStatusCode());
		    e.printStackTrace();
		    throw e;
		} catch (Exception e) {
		    System.out.println("네이버페이 결제 승인 실패 - 기타 예외 ");
		    e.printStackTrace();
		    throw e;
		}
		return body;
	}

	@Override
	public Map<String, String> requestApprovalNaverpayCancel(String paymentId, String cancelReason,
			Integer cancelAmount) {
		String requestUrl = "https://dev.apis.naver.com/naverpay-partner/naverpay/payments/v1/cancel";
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Naver-Client-Id", this.naverClientId);
		headers.set("X-Naver-Client-Secret", this.naverClientSecret);
		headers.set("X-NaverPay-Chain-Id", this.naverChainId);
		headers.set("X-NaverPay-Idempotency-Key", UUID.randomUUID().toString());
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
		
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("paymentId", paymentId);
		form.add("cancelAmount", cancelAmount+"");
		form.add("taxScopeAmount", cancelAmount+"");
		form.add("taxExScopeAmount", "0");
		form.add("cancelReason", cancelReason);
		form.add("cancelRequester", "2");
		
		HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);
		
		Map<String, String> resultMap = new HashMap<>();
		try {
			ResponseEntity<String> response = this.restTemplate.postForEntity(requestUrl, entity, String.class);
			resultMap.put("status", response.getStatusCodeValue() + "");
			resultMap.put("response", response.getBody());
		} catch (HttpStatusCodeException e) {
		    resultMap.put("status", e.getRawStatusCode() + "");
		    resultMap.put("response", e.getResponseBodyAsString());
		    System.out.println("네이버페이 결제취소 승인 실패 - HTTP " + e.getRawStatusCode());
		    e.printStackTrace();
		    throw e;
		} catch (Exception e) {
			resultMap.put("response", null);
		    System.out.println("네이버페이 결제취소 승인 실패 - 기타 예외 ");
		    e.printStackTrace();
		    throw new RuntimeException("결제 승인 실패", e);
		}
		
		return resultMap;
	}

	@Override
	public Map<String, String> requestApprovalKakaopayCancel(String paymentId, String cancelReason,
			Integer cancelAmount) {
		String requestUrl = "https://open-api.kakaopay.com/online/v1/payment/cancel";
		
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "SECRET_KEY " + this.kakaoSecretKey);
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		JsonObject root = new JsonObject();
		root.addProperty("cid", "TC0ONETIME");
		root.addProperty("tid", paymentId);
		root.addProperty("cancel_amount", cancelAmount);
		root.addProperty("cancel_tax_free_amount", 0);
		String jsonBody = gson.toJson(root);
		
		HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);
		Map<String, String> resultMap = new HashMap<>();
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, requestEntity, String.class);
			resultMap.put("status", response.getStatusCodeValue() + "");
			resultMap.put("response", response.getBody());
		} catch (HttpStatusCodeException e) {
			resultMap.put("status", e.getRawStatusCode() + "");
			resultMap.put("response", e.getResponseBodyAsString());
			System.out.println("카카오페이 결제취소 승인 실패 - HTTP " + e.getRawStatusCode());
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			System.out.println("카카오페이 결제취소 승인 실패 - 기타 예외 ");
			e.printStackTrace();
			throw e;
		}
		return resultMap;
	}

	@Override
	public Map<String, String> readyKakaoPay(String name, int amount, HttpServletRequest request) {
		/* localhost 주소를 사용해도 잘 되지 않나?
		String baseUrl = String.format("%s://%s:%d%s", request.getScheme(), // http or https
				request.getServerName(), // localhost or actual server domain
				request.getServerPort(), // 8080 or actual port
				request.getContextPath()); // application context
		System.out.println("baseUrl : " + baseUrl);
		*/
		
		String requestUrl = "https://open-api.kakaopay.com/online/v1/payment/ready";
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "SECRET_KEY" + this.kakaoSecretKey);
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		JsonObject root = new JsonObject();
		root.addProperty("cid", "TC0ONETIME");
		root.addProperty("partner_order_id", "partner_order_id");
		root.addProperty("partner_user_id", "partner_user_id");
		root.addProperty("item_name", name);
		root.addProperty("quantity", 1);
		root.addProperty("total_amount", amount);
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
			resultMap.put("status", response.getStatusCodeValue() + "");
			resultMap.put("response", response.getBody());
		} catch (HttpStatusCodeException e) {
			resultMap.put("status", e.getRawStatusCode() + "");
			resultMap.put("response", e.getResponseBodyAsString());
			System.out.println("카카오페이 결제준비 실패 - HTTP " + e.getRawStatusCode());
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			System.out.println("카카오페이 결제취소 승인 실패 - 기타 예외 ");
			e.printStackTrace();
			throw e;
		}
		return resultMap;
	}

	@Override
	public Map<String, String> requestApprovalKakaopayPayment(String tid, String pg_token) {
		String requestUrl = "https://open-api.kakaopay.com/online/v1/payment/approve";
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "SECRET_KEY" + this.kakaoSecretKey);
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		JsonObject root = new JsonObject();
		root.addProperty("cid", "TC0ONETIME");
		root.addProperty("partner_order_id", "partner_order_id");
		root.addProperty("partner_user_id", "partner_user_id");
		root.addProperty("tid", tid);
		root.addProperty("pg_token", pg_token);
		String jsonBody = gson.toJson(root);
		
		HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers); 
		Map<String, String> resultMap = new HashMap<>();
		try {
			ResponseEntity<String> response = restTemplate.postForEntity(requestUrl, requestEntity, String.class);
			resultMap.put("status", response.getStatusCodeValue() + "");
			resultMap.put("response", response.getBody());
		} catch (HttpStatusCodeException e) {
			resultMap.put("status", e.getRawStatusCode() + "");
			resultMap.put("response", e.getResponseBodyAsString());
			System.out.println("카카오페이 결제승인 실패 - HTTP " + e.getRawStatusCode());
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			System.out.println("카카오페이 결제승인 실패 - 기타 예외 ");
			e.printStackTrace();
			throw e;
		}
		return resultMap;
	}

	@Override
	public List<OrderProductsDTO> getOrderListOfMember(String memberId) {
		List<OrderProductsDTO> result = new ArrayList<>();
		List<String> orders = orderDAO.getOrderIdList(memberId);
		System.out.println(memberId + "의 order id 리스트 : " + orders);
		for (String orderId : orders) {
			OrderProductsDTO order = new OrderProductsDTO();
			order.setOrderId(orderId);
			Map<String, Object> orderInfo = orderDAO.getOrderInfo(orderId);
			// System.out.println("orderInfo.order_id : " + (String)
			// orderInfo.get("order_id"));
			// System.out.println("orderInfo : " + orderInfo);
			// System.out.println("orderInfo.order_status : " + (String)
			// orderInfo.get("order_status"));
			Timestamp time = (Timestamp) orderInfo.get("order_date");
			order.setOrderDate( time.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) );
			Map<String, String> dict = new HashMap<>();
			dict.put("1", "결제대기");
			dict.put("2", "결제완료");
			dict.put("3", "상품준비중");
			dict.put("4", "배송준비중");
			dict.put("5", "배송중");
			dict.put("6", "배송완료");
			order.setOrderStatus(dict.get((String) orderInfo.get("order_status")));
			order.setPayMethod((String) orderInfo.get("payment_module_type"));
			List<OrderProductDTO> products = orderDAO.getProductList(orderId);
			// System.out.println("products : " + products);
			order.setProducts(products);
			System.out.println("order in getOrderListOfMember: " + order);
			result.add(order);
		}
		return result;
	}
	
	@Override
	public List<OrderProductsDTO> getOrderListOfNonMember(String name, String phoneNumber, String email) {
		List<OrderProductsDTO> result = new ArrayList<>();
		List<String> orders = orderDAO.getOrderIdList(name, phoneNumber, email);
		System.out.println("비회원 " + name + "님 의 order id 리스트 : " + orders);
		for (String orderId : orders) {
			OrderProductsDTO order = new OrderProductsDTO();
			order.setOrderId(orderId);
			Map<String, Object> orderInfo = orderDAO.getOrderInfo(orderId);
			// System.out.println("orderInfo.order_id : " + (String) orderInfo.get("order_id"));
			// System.out.println("orderInfo : " + orderInfo);
			// System.out.println("orderInfo.order_status : " + (String) orderInfo.get("order_status"));
			Timestamp time = (Timestamp) orderInfo.get("order_date");
			order.setOrderDate( time.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) );
			Map<String, String> dict = new HashMap<>();
			dict.put("1", "결제대기");
			dict.put("2", "결제완료");
			dict.put("3", "상품준비중");
			dict.put("4", "배송준비중");
			dict.put("5", "배송중");
			dict.put("6", "배송완료");
			order.setOrderStatus(dict.get((String) orderInfo.get("order_status")));
			List<OrderProductDTO> products = orderDAO.getProductList(orderId);
			System.out.println("products : " + products);
			order.setProducts(products);
			System.out.println("order : " + order);
			result.add(order);
		}
		return result;
	}

	@Override
	@Transactional(rollbackFor = { Exception.class })
	public void cancelOrder(CancelOrderRequestDTO request) throws Exception {
		orderDAO.makeCancel(
				request.getOrderId(),
				request.getProducts(),
				request.getCancelType(),
				request.getCancelReason());
		orderDAO.updateAccountInfo(
				request.getOrderId(),
				request.getAccountOwner(),
				request.getAccountBank(),
				request.getAccountNumber()
				);
	}
	
	@Override
	public void updateOrderStatusAuto() {
		orderDAO.updateOrderStatusAuto();
	}
	
	
}
