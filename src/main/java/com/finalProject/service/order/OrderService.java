package com.finalProject.service.order;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.ui.Model;

import com.finalProject.model.order.CancelOrderRequestDTO;
import com.finalProject.model.order.OrderMemberDTO;
import com.finalProject.model.order.OrderProductDTO;
import com.finalProject.model.order.OrderProductsDTO;
import com.finalProject.model.order.OrderRequestDTO;
import com.finalProject.model.order.PaymentRequestDTO;
import com.finalProject.payment.PaymentResult;

public interface OrderService {
	
	List<OrderProductDTO> getProductInfo(List<OrderRequestDTO> requestsInfo);

	OrderMemberDTO getMemberInfo(String memberId);
			
	void setPaymentModuleKey(String orderId, String key) throws Exception;
	
	String getPaymentModuleKey(String orderId);

	int getExpectedTotalPrice(String orderId);
	
	void deleteOrder(String orderId);
	
	void makePayment(String orderId, Integer amount, String payModule, String method, HttpSession session) throws Exception;
	
	void deletePaidProductsFromCart(String memberId);
	
	Map<String, Object> makeOrder(PaymentRequestDTO request, boolean isMember, HttpSession session) throws Exception;
	
	void makeGuest(PaymentRequestDTO request, String orderId);

	List<OrderProductsDTO> getOrderListOfMember(String memberId);
	
	List<OrderProductsDTO> getOrderListOfNonMember(String name, String phoneNumber, String email);

	void cancelOrder(CancelOrderRequestDTO request) throws Exception;

	void updateOrderStatusAuto();

	PaymentResult approvePayment(String paymentType, String paymentKey, String orderId, int amount, String pgToken);

	void deleteCart(HttpSession session, Model model);

	PaymentResult readyPayment(String paymentType, String orderName, int amount);

	PaymentResult cancelPayment(String paymentType, String paymentKey, int amount, String cancelReason);

}
