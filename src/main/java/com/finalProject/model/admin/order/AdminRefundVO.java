package com.finalProject.model.admin.order;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AdminRefundVO {
	private int refund_no;
	private String cancel_no;
	private int payment_num;
	private String refund_type;
	private Date refund_receive_date;
	private int refund_amount;
	
    @Override
    public AdminRefundVO clone() {
        try {
            return (AdminRefundVO) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }
}
