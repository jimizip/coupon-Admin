package com.example.coupon_admin.validator;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

// CSV 파일 검증 전략
@Component
public class CsvFileValidator implements FileValidatorStrategy {

    @Override
    public ValidationResult validate(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            // 1. 헤더 검증
            String header = reader.readLine();
            if (header == null || !header.trim().equals("customer_id")) {
                return ValidationResult.failure("Invalid header. Expected 'customer_id'.");
            }

            // 2. 내용 비어있는지 검증
            if (reader.readLine() == null) {
                return ValidationResult.failure("File is empty.");
            }

            // 모든 검증 통과
            return ValidationResult.success();

        } catch (Exception e) {
            return ValidationResult.failure("Error while reading CSV file: " + e.getMessage());
        }
    }
}
