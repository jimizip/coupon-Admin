package com.example.coupon_admin.validator;

import java.io.InputStream;

/**
 * 파일 검증 전략 인터페이스
 * CSV, Excel 등 다양한 파일 타입에 대한 검증 전략을 정의합니다.
 */
public interface FileValidatorStrategy {
    /**
     * 파일의 유효성을 검증합니다.
     *
     * @param inputStream 검증할 파일의 InputStream
     * @return ValidationResult 검증 결과
     */
    ValidationResult validate(InputStream inputStream);
}
