package com.example.coupon_admin.validator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 파일 확장자에 따라 적절한 검증 전략을 반환하는 팩토리
@Component
@RequiredArgsConstructor
public class FileValidatorFactory {

    private final CsvFileValidator csvFileValidator;
    private final ExcelFileValidator excelFileValidator;

    /**
     * 파일명의 확장자를 기반으로 적절한 검증 전략을 반환합니다.
     *
     * @param filename 파일명
     * @return FileValidatorStrategy 검증 전략
     * @throws IllegalArgumentException 지원하지 않는 파일 타입인 경우
     */
    public FileValidatorStrategy getValidator(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("파일명이 비어 있습니다.");
        }

        String extension = getFileExtension(filename).toLowerCase();

        return switch (extension) {
            case "csv" -> csvFileValidator;
            case "xlsx" -> excelFileValidator;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 파일 형식입니다. (.csv 또는 .xlsx 파일만 업로드 가능합니다)"
            );
        };
    }

    /**
     * 파일명에서 확장자를 추출합니다.
     *
     * @param filename 파일명
     * @return 확장자 (점 제외)
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
}
