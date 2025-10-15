package com.example.coupon_admin.global.exception;

import com.example.coupon_admin.global.BaseCode;
import com.example.coupon_admin.global.ReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GeneralException extends RuntimeException{
    private BaseCode code;

    public ReasonDTO getErrorReasonHttpStatus(){
        return this.code.getReasonHttpStatus();
    }
}
