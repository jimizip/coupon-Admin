package com.example.coupon_admin.validator;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Excel 파일 검증 전략 (.xlsx)
 */
@Component
public class ExcelFileValidator implements FileValidatorStrategy {

    @Override
    public ValidationResult validate(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            // 1. 시트 개수 확인
            if (workbook.getNumberOfSheets() == 0) {
                return ValidationResult.failure("Excel file has no sheets.");
            }

            // 2. 첫 번째 시트 가져오기
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                return ValidationResult.failure("Excel sheet is empty.");
            }

            // 3. 헤더 행 검증
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return ValidationResult.failure("Header row is missing.");
            }

            Cell firstCell = headerRow.getCell(0);
            if (firstCell == null) {
                return ValidationResult.failure("Header cell is missing.");
            }

            String headerValue = firstCell.getStringCellValue();
            if (headerValue == null || !headerValue.trim().equals("customer_id")) {
                return ValidationResult.failure("Invalid header. Expected 'customer_id'.");
            }

            // 4. 데이터 행 존재 여부 확인
            if (sheet.getPhysicalNumberOfRows() < 2) {
                return ValidationResult.failure("File is empty.");
            }

            // 모든 검증 통과
            return ValidationResult.success();

        } catch (Exception e) {
            return ValidationResult.failure("Error while reading Excel file: " + e.getMessage());
        }
    }
}
