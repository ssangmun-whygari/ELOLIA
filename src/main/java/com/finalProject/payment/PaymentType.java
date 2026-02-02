package com.finalProject.payment;

public enum PaymentType {
	TOSS("T"),
    KAKAO("K"),
    NAVER("N");

	private final String code;

    PaymentType(String code) {
    	this.code = code;
    }

    public String getCode() {
    	return code;
    }

    public static PaymentType fromCode(String code) {
    	for (PaymentType type : values()) {
        	if (type.code.equals(code)) {
        		return type;
        	}
        }
        throw new IllegalArgumentException("Unknown payment type: " + code);
    }
}