package com.finalProject.model.admin.order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class AdminPaymentVO {
	private String payment_module_key;
	private int paid_amount;
	private String cancel_reason;
	private String payment_method;
	private int payment_no;
	private String cancel_type;
	private int assigned_point;
}
