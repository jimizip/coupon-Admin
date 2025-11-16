# 스토리지 추상화 가이드

## 목차
- [리팩토링 배경](#리팩토링-배경)
- [아키텍처 변경](#아키텍처-변경)
- [주요 변경 사항](#주요-변경-사항)
- [새로 생성된 파일](#새로-생성된-파일)
- [수정된 파일](#수정된-파일)
- [다른 클라우드로 확장하기](#다른-클라우드로-확장하기)
- [마이그레이션 영향](#마이그레이션-영향)

---

## 리팩토링 배경

### 문제점

기존 구조는 서비스 계층이 AWS S3 SDK에 직접 의존하고 있었습니다:

```java
@Service
public class FileUploadService {
    private final S3Client s3Client;  // AWS SDK에 직접 의존

    public Long upload(MultipartFile file) {
        s3Client.putObject(...);  // AWS 특화 API 직접 호출
    }
}
```

**이로 인한 문제:**
1. **클라우드 벤더 종속성**: AWS S3에서 다른 클라우드로 전환 시 모든 서비스 코드 수정 필요
2. **테스트 어려움**: 실제 S3 또는 LocalStack 없이 단위 테스트 불가
3. **확장성 부족**: Azure Blob Storage, GCP Cloud Storage 등 다른 스토리지 사용 시 대규모 리팩토링 필요
4. **도메인 오염**: 도메인 엔티티(`UploadFile`)의 필드명이 `s3FilePath`로 특정 기술에 종속

### 목적

- **클라우드 벤더 독립성**: 스토리지 제공자 변경 시 최소한의 코드 수정
- **테스트 용이성**: Mock 객체로 쉽게 대체 가능
- **확장성**: 새로운 스토리지 제공자 추가가 용이한 구조
- **관심사 분리**: 비즈니스 로직과 인프라 계층의 명확한 분리

---

## 아키텍처 변경

### 변경 전: 직접 의존

```
┌─────────────────────┐
│  Service Layer      │
│                     │
│ FileUploadService   │──┐
│ FileDownloadService │  │  직접 의존
│ FileValidationService│ │
└─────────────────────┘  │
                         ▼
                  ┌─────────────┐
                  │  S3Client   │
                  │ S3Presigner │  (AWS SDK)
                  └─────────────┘
```

**문제점:**
- Service 계층이 AWS SDK에 직접 의존
- 클라우드 전환 시 모든 Service 수정 필요
- AWS SDK import가 Service 코드에 산재

### 변경 후: 추상화 계층 도입

```
┌─────────────────────┐
│  Service Layer      │
│                     │
│ FileUploadService   │──┐
│ FileDownloadService │  │
│ FileValidationService│ │  인터페이스 의존
└─────────────────────┘  │
                         ▼
                  ┌──────────────────┐
                  │ StorageService   │  (인터페이스)
                  │  - uploadFile()  │
                  │  - downloadFile()│
                  │  - presignedUrl()│
                  └──────────────────┘
                           △
                           │ 구현
              ┌────────────┼────────────┐
              │            │            │
      ┌──────────────┐ ┌────────────┐ ┌─────────────┐
      │S3Storage     │ │AzureStorage│ │GcpStorage   │
      │Service       │ │Service     │ │Service      │
      └──────────────┘ └────────────┘ └─────────────┘
```

**장점:**
- Service 계층은 `StorageService` 인터페이스만 의존
- 새로운 클라우드 추가 시 구현체만 추가
- 설정만으로 스토리지 전환 가능

---

## 주요 변경 사항

### 1. 추상화 인터페이스 생성

**StorageService.java**
```java
public interface StorageService {
    void uploadFile(String key, InputStream content, long size, String contentType) throws IOException;
    InputStream downloadFile(String key) throws IOException;
    String generatePresignedUrl(String key, Duration expiration) throws IOException;
}
```

- 클라우드 벤더 독립적인 메서드 정의
- 공통 파라미터만 사용 (AWS, Azure, GCP 모두 지원 가능)
- `IOException`으로 예외 추상화

### 2. S3 구현체 생성

**S3StorageService.java**
```java
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Override
    public void uploadFile(String key, InputStream content, long size, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(size)
            .build();
        s3Client.putObject(request, RequestBody.fromInputStream(content, size));
    }

    // downloadFile(), generatePresignedUrl() 구현...
}
```

- 기존 Service 계층의 S3 로직을 모두 이동
- AWS SDK 의존성을 이 클래스에만 격리
- 다른 Service는 AWS를 전혀 모름

### 3. Service 계층 리팩토링

**변경 전:**
```java
@Service
public class FileUploadService {
    private final S3Client s3Client;

    public Long upload(MultipartFile file) throws IOException {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();
        s3Client.putObject(request, ...);
    }
}
```

**변경 후:**
```java
@Service
public class FileUploadService {
    private final StorageService storageService;

    public Long upload(MultipartFile file) throws IOException {
        storageService.uploadFile(key, content, size, contentType);
    }
}
```

- AWS SDK import 제거
- `StorageService` 인터페이스에만 의존
- 비즈니스 로직은 그대로 유지

### 4. 도메인 엔티티 개선

**UploadFile.java**
```java
@Entity
public class UploadFile {
    // 변경 전
    private String s3FilePath;  // ❌ AWS 특화 필드명

    // 변경 후
    private String storagePath; // ✅ 클라우드 독립적 필드명
}
```

---

## 새로 생성된 파일

### 1. storage/StorageService.java

**역할**: 스토리지 추상화 인터페이스

**주요 메서드:**
- `uploadFile()`: 파일 업로드
- `downloadFile()`: 파일 다운로드 (InputStream 반환)
- `generatePresignedUrl()`: 임시 다운로드 URL 생성

**특징:**
- 클라우드 벤더 독립적
- 표준 Java 타입만 사용 (InputStream, String, Duration)
- 
### 2. storage/S3StorageService.java

**역할**: AWS S3 구현체

**의존성:**
- `S3Client`: 파일 업로드/다운로드
- `S3Presigner`: Pre-signed URL 생성
- `@Value("${cloud.aws.s3.bucket}")`: 버킷명 주입

**특징:**
- 기존 Service 계층의 S3 로직을 모두 포함
- AWS SDK 의존성을 이 클래스에만 격리

---

## 다른 클라우드로 확장하기

### Azure Blob Storage 추가 예시

1. Azure 구현체 생성

2. Azure 설정 추가

3. Azure로 전환

---

## 참고 자료

- [AWS SDK for Java v2](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [Azure Blob Storage for Java](https://learn.microsoft.com/azure/storage/blobs/storage-quickstart-blobs-java)
- [Google Cloud Storage Client Libraries](https://cloud.google.com/storage/docs/reference/libraries)
- [Dependency Inversion Principle (DIP)](https://en.wikipedia.org/wiki/Dependency_inversion_principle)
- [Strategy Pattern](https://refactoring.guru/design-patterns/strategy)
