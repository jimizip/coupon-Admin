# 파일 검증 전략 패턴 가이드

## 목차
- [도입 배경](#도입-배경)
- [전략 패턴이란](#전략-패턴이란)
- [아키텍처 구조](#아키텍처-구조)
- [구현 세부사항](#구현-세부사항)
- [사용 방법](#사용-방법)
- [새로운 파일 타입 추가하기](#새로운-파일-타입-추가하기)
- [장점 및 효과](#장점-및-효과)

---

## 도입 배경

### 기존 문제점

#### 1. 하드코딩된 CSV 검증 로직
```java
// 기존 코드: CSV만 지원하는 하드코딩된 검증
public void validateFile(Long fileId) {
    // ...
    try (BufferedReader reader = new BufferedReader(...)) {
        String header = reader.readLine();
        if (header == null || !header.trim().equals("customer_id")) {
            uploadFile.fail("Invalid header. Expected 'customer_id'.");
            return;
        }
        // ...
    }
}
```

**문제점:**
- CSV 파일만 검증 가능
- Excel 파일 업로드 시 바이너리 데이터를 텍스트로 읽어 실패
- 새로운 파일 타입 추가 시 기존 코드 수정 필요 (OCP 위반)
- if/switch 분기가 계속 추가되어 코드 복잡도 증가

#### 2. 확장성 부족
```java
// if/switch가 계속 추가
public void validateFile(Long fileId) {
    String extension = getExtension(filename);

    if (extension.equals("csv")) {
        // CSV 검증 로직
    } else if (extension.equals("xlsx")) {
        // Excel 검증 로직
    } else if (extension.equals("json")) {  // 새 타입 추가 시마다 수정
        // JSON 검증 로직
    } else if (extension.equals("xml")) {   // 계속 추가...
        // XML 검증 로직
    }
}
```

### 요구사항
- CSV와 Excel 파일 모두 지원
- 향후 다른 파일 타입(JSON, XML 등) 추가 가능
- 기존 코드 수정 없이 확장 가능 (Open-Closed Principle)
- 각 파일 타입별 검증 로직 독립적 관리

---

## 전략 패턴이란

### 정의
전략 패턴(Strategy Pattern)은 **알고리즘군을 정의하고, 각각을 캡슐화하여 교환 가능하게 만드는** 디자인 패턴입니다.

### 핵심 개념
```
┌─────────────────┐
│  FileValidator  │ (인터페이스)
│  Strategy       │
└────────▲────────┘
         │
    ┌────┴────┬────────┬────────┐
    │         │        │        │
┌───▼──┐  ┌──▼───┐ ┌──▼───┐ ┌──▼───┐
│ CSV  │  │Excel │ │ JSON │ │ XML  │
│Vali  │  │Vali  │ │Vali  │ │Vali  │
│dator │  │dator │ │dator │ │dator │
└──────┘  └──────┘ └──────┘ └──────┘
```

- **Context**: `FileValidationService` - 전략을 사용하는 클래스
- **Strategy**: `FileValidatorStrategy` - 공통 인터페이스
- **Concrete Strategy**: `CsvFileValidator`, `ExcelFileValidator` - 구체적 전략 구현
- **Factory**: `FileValidatorFactory` - 적절한 전략 선택

---

## 아키텍처 구조

### 디렉토리 구조
```
com.example.coupon_admin/
├─ validator/
│  ├─ FileValidatorStrategy.java     # 전략 인터페이스
│  ├─ CsvFileValidator.java          # CSV 검증 전략
│  ├─ ExcelFileValidator.java        # Excel 검증 전략
│  ├─ FileValidatorFactory.java      # 전략 팩토리
│  └─ ValidationResult.java          # 검증 결과 DTO
└─ service/
   └─ FileValidationService.java     # 전략 사용 (Context)
```

### 클래스 다이어그램
```
┌──────────────────────────────────────┐
│   <<interface>>                      │
│   FileValidatorStrategy              │
├──────────────────────────────────────┤
│ + validate(InputStream): Result      │
└─────────────▲───────────────────────┘
              │
              │ implements
     ┌────────┴─────────┐
     │                  │
┌────▼────────────┐  ┌─▼─────────────────┐
│CsvFileValidator │  │ExcelFileValidator │
├─────────────────┤  ├───────────────────┤
│ + validate()    │  │ + validate()      │
└─────────────────┘  └───────────────────┘
         ▲                    ▲
         │                    │
         └────────┬───────────┘
                  │ uses
     ┌────────────▼──────────────────┐
     │ FileValidatorFactory          │
     ├───────────────────────────────┤
     │ + getValidator(filename)      │
     └───────────────────────────────┘
                  ▲
                  │ uses
     ┌────────────▼──────────────────┐
     │ FileValidationService         │
     ├───────────────────────────────┤
     │ - validatorFactory            │
     │ + validateFile(fileId)        │
     └───────────────────────────────┘
```

### 데이터 흐름
```
[파일 업로드]
     │
     ▼
[FileValidationService.validateFile()]
     │
     ├─1. 파일명에서 확장자 추출
     │
     ├─2. FileValidatorFactory.getValidator(filename)
     │       │
     │       ├─ 확장자 == "csv"  → CsvFileValidator
     │       ├─ 확장자 == "xlsx" → ExcelFileValidator
     │       └─ 그 외 → IllegalArgumentException
     │
     ├─3. S3에서 파일 다운로드 (InputStream)
     │
     ├─4. validator.validate(inputStream)
     │       │
     │       └─ ValidationResult 반환 (valid, errorMessage)
     │
     └─5. 결과 처리
          ├─ valid == true  → uploadFile.complete()
          └─ valid == false → uploadFile.fail(errorMessage)
```

---

## 구현 세부사항

### 1. 전략 인터페이스

#### FileValidatorStrategy.java
```java
package com.example.coupon_admin.validator;

import java.io.InputStream;

/**
 * 파일 검증 전략 인터페이스
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
```

### 2. 검증 결과 DTO

#### ValidationResult.java
```java
package com.example.coupon_admin.validator;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ValidationResult {
    private final boolean valid;
    private final String errorMessage;

    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult failure(String errorMessage) {
        return new ValidationResult(false, errorMessage);
    }
}
```

**DTO를 사용하는 이유:**
- 성공/실패 여부 + 에러 메시지를 한 번에 반환
- 예외 대신 정상 흐름으로 처리 (검증 실패는 예상 가능한 상황)
- 향후 확장 가능 (totalRows, validRows, warnings 등 추가 가능)

### 3. CSV 검증 전략

#### CsvFileValidator.java
```java
package com.example.coupon_admin.validator;

import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

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

            // 2. 데이터 행 존재 여부 확인
            if (reader.readLine() == null) {
                return ValidationResult.failure("File is empty.");
            }

            return ValidationResult.success();

        } catch (Exception e) {
            return ValidationResult.failure("Error while reading CSV file: " + e.getMessage());
        }
    }
}
```

**구현 특징:**
- `BufferedReader`로 텍스트 파일 읽기
- "customer_id" 헤더 검증
- 최소 1개 이상의 데이터 행 확인
- 예외 발생 시 실패 결과 반환

### 4. Excel 검증 전략

#### ExcelFileValidator.java
```java
package com.example.coupon_admin.validator;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.InputStream;

@Component
public class ExcelFileValidator implements FileValidatorStrategy {

    @Override
    public ValidationResult validate(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            // 1. 시트 존재 여부 확인
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

            return ValidationResult.success();

        } catch (Exception e) {
            return ValidationResult.failure("Error while reading Excel file: " + e.getMessage());
        }
    }
}
```

**구현 특징:**
- Apache POI `XSSFWorkbook` 사용 (.xlsx 파싱)
- 첫 번째 시트의 첫 번째 셀 검증
- 최소 2개 행 (헤더 + 데이터) 확인
- 바이너리 파일 정상 처리

**의존성:**
```gradle
implementation 'org.apache.poi:poi:5.2.5'
implementation 'org.apache.poi:poi-ooxml:5.2.5'
```

### 5. 전략 팩토리

#### FileValidatorFactory.java
```java
package com.example.coupon_admin.validator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileValidatorFactory {

    private final CsvFileValidator csvFileValidator;
    private final ExcelFileValidator excelFileValidator;

    /**
     * 파일명의 확장자를 기반으로 적절한 검증 전략을 반환합니다.
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

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }
}
```

**구현 특징:**
- Spring의 의존성 주입으로 전략 객체 관리
- switch expression (Java 14+) 사용
- 확장자 기반 전략 선택
- 지원하지 않는 타입은 명확한 에러 메시지

### 6. Context (전략 사용)

#### FileValidationService.java
```java
package com.example.coupon_admin.service;

import com.example.coupon_admin.domain.UploadFile;
import com.example.coupon_admin.repository.UploadFileRepository;
import com.example.coupon_admin.validator.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Service
@RequiredArgsConstructor
public class FileValidationService {

    private final S3Client s3Client;
    private final UploadFileRepository uploadFileRepository;
    private final FileValidatorFactory validatorFactory;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Async
    public void validateFile(Long fileId) {
        UploadFile uploadFile = uploadFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        try {
            // 1. 전략 선택
            FileValidatorStrategy validator = validatorFactory.getValidator(
                    uploadFile.getOriginalFileName()
            );

            // 2. S3에서 파일 다운로드
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(uploadFile.getS3FilePath())
                    .build();

            // 3. 전략에 검증 위임
            try (ResponseInputStream<GetObjectResponse> s3ObjectInputStream =
                    s3Client.getObject(getObjectRequest)) {

                ValidationResult result = validator.validate(s3ObjectInputStream);

                // 4. 결과 처리
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
```

**구현 특징:**
- 팩토리에서 전략을 가져와 사용
- S3 파일 스트림을 전략에 전달
- 검증 결과에 따라 성공/실패 처리
- 비동기 실행 (@Async)

---

## 새로운 파일 타입 추가하기

### 예시: JSON 파일 지원 추가

#### 1. 의존성 추가 (필요시)
```gradle
implementation 'com.fasterxml.jackson.core:jackson-databind'
```

#### 2. 전략 구현체 생성
```java
// JsonFileValidator.java
package com.example.coupon_admin.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.InputStream;

@Component
public class JsonFileValidator implements FileValidatorStrategy {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ValidationResult validate(InputStream inputStream) {
        try {
            JsonNode root = objectMapper.readTree(inputStream);

            // JSON 배열인지 확인
            if (!root.isArray() || root.size() == 0) {
                return ValidationResult.failure("JSON file must contain an array with data.");
            }

            // 첫 번째 객체에 customer_id 필드가 있는지 확인
            JsonNode firstElement = root.get(0);
            if (!firstElement.has("customer_id")) {
                return ValidationResult.failure("JSON objects must have 'customer_id' field.");
            }

            return ValidationResult.success();

        } catch (Exception e) {
            return ValidationResult.failure("Error while reading JSON file: " + e.getMessage());
        }
    }
}
```

#### 3. 팩토리에 전략 추가
```java
// FileValidatorFactory.java
@Component
@RequiredArgsConstructor
public class FileValidatorFactory {

    private final CsvFileValidator csvFileValidator;
    private final ExcelFileValidator excelFileValidator;
    private final JsonFileValidator jsonFileValidator;  // 추가

    public FileValidatorStrategy getValidator(String filename) {
        // ...

        return switch (extension) {
            case "csv" -> csvFileValidator;
            case "xlsx" -> excelFileValidator;
            case "json" -> jsonFileValidator;  // 추가
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 파일 형식입니다. (.csv, .xlsx, .json 파일만 업로드 가능합니다)"
            );
        };
    }
}
```

**끝! 기존 코드 수정 없이 새로운 파일 타입 지원 완료**

---

## 장점 및 효과

### 1. 개방-폐쇄 원칙 (OCP) 준수
✅ **확장에는 열려있고, 수정에는 닫혀있다**
- 새로운 파일 타입 추가 시 기존 코드 수정 불필요
- 새 전략 클래스만 추가하면 됨

### 2. 단일 책임 원칙 (SRP) 준수
✅ **각 클래스는 하나의 책임만**
- `CsvFileValidator`: CSV 검증만
- `ExcelFileValidator`: Excel 검증만
- `FileValidatorFactory`: 전략 선택만
- `FileValidationService`: 전체 검증 흐름 관리만

### 3. 코드 가독성 및 유지보수성 향상
```java
// ✅ 전략 패턴: 깔끔한 위임
FileValidatorStrategy validator = factory.getValidator(filename);
ValidationResult result = validator.validate(inputStream);
```

### 4. 테스트 용이성
```java
// 각 전략을 독립적으로 테스트 가능
@Test
void csvValidator_validFile_returnsSuccess() {
    CsvFileValidator validator = new CsvFileValidator();
    InputStream inputStream = createCsvInputStream("customer_id\n123");

    ValidationResult result = validator.validate(inputStream);

    assertTrue(result.isValid());
}
```

### 5. 런타임 전략 변경 가능
```java
// 파일 타입에 따라 런타임에 전략이 자동으로 선택됨
validator = factory.getValidator("data.csv");   // CsvFileValidator
validator = factory.getValidator("data.xlsx");  // ExcelFileValidator
validator = factory.getValidator("data.json");  // JsonFileValidator
```

---

## 비교: Before & After

### Before (전략 패턴 적용 전)
```
- 하드코딩된 CSV 검증
- Excel 지원 불가
- 새 타입 추가 시 기존 코드 수정 필요
- 복잡한 if/switch 분기
- 테스트 어려움
```

### After (전략 패턴 적용 후)
```
- CSV와 Excel 모두 지원
- 새 타입 추가 시 새 클래스만 작성
- 깔끔한 코드 구조
- 각 전략 독립적으로 테스트 가능
- SOLID 원칙 준수
```

---

## 결론

전략 패턴을 적용하여:
- **확장 가능한** 파일 검증 시스템 구축
- **유지보수하기 쉬운** 코드 구조
- **SOLID 원칙**을 준수하는 객체지향 설계
- **테스트 가능한** 독립적인 모듈