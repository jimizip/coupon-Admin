package com.example.coupon_admin.service;

import com.example.coupon_admin.domain.UploadFile;
import com.example.coupon_admin.repository.UploadFileRepository;
import com.example.coupon_admin.storage.StorageService;
import com.example.coupon_admin.validator.FileValidatorFactory;
import com.example.coupon_admin.validator.FileValidatorStrategy;
import com.example.coupon_admin.validator.ValidationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class FileValidationService {

    private final StorageService storageService;
    private final UploadFileRepository uploadFileRepository;
    private final FileValidatorFactory validatorFactory;

    @Async // 비동기
    public void validateFile(Long fileId) {
        UploadFile uploadFile = uploadFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        try {
            // 1. 파일명을 기반으로 적절한 검증 전략 선택
            FileValidatorStrategy validator = validatorFactory.getValidator(uploadFile.getOriginalFileName());

            // 2. 스토리지에서 파일 다운로드
            try (InputStream fileStream = storageService.downloadFile(uploadFile.getStoragePath())) {
                // 3. 전략에 검증 위임
                ValidationResult result = validator.validate(fileStream);

                // 4. 검증 결과에 따라 처리
                if (result.isValid()) {
                    uploadFile.complete();
                } else {
                    uploadFile.fail(result.getErrorMessage());
                }
            }

        } catch (Exception e) {
            uploadFile.fail("Validation error: " + e.getMessage());
        }
    }
}
