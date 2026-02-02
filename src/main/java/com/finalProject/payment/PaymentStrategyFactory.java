package com.finalProject.payment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

@Component
public class PaymentStrategyFactory {
	private final List<PaymentStrategy> strategies;
	private Map<PaymentType, PaymentStrategy> strategyMap;
	
	// 스프링이 모든 PaymentStrategy 구현체를 자동 주입
	public PaymentStrategyFactory(List<PaymentStrategy> strategies) {
		this.strategies = strategies;
	}
	
	@PostConstruct
	public void init() {
		strategyMap = new HashMap<>();
		for (PaymentStrategy strategy : strategies) {
			strategyMap.put(strategy.getType(), strategy);
		}
	}
	
	// 예 : strategy.getStrategy(PaymentType.TOSS)
	public PaymentStrategy getStrategy(PaymentType type) {
	PaymentStrategy strategy = strategyMap.get(type);
		if (strategy == null) {
			throw new IllegalArgumentException("지원하지 않는 결제 수단: " + type);
	    }
		return strategy;
	}
	
	// 예 : strategy.getStrtegy("T");
	public PaymentStrategy getStrategy(String typeCode) {
	    return getStrategy(PaymentType.fromCode(typeCode));
	}
}
