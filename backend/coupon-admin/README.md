# Coupon Admin Service

쿠폰 관리 시스템의 백엔드 서비스입니다. CSV 및 Excel 파일을 업로드하고 검증하는 기능을 제공합니다.

## 기술 스택

- **Java 21**
- **Spring Boot 3.5.6**
- **Spring Data JPA**
- **PostgreSQL**
- **AWS SDK v2** (S3)
- **Apache POI** (Excel 파일 처리)
- **LocalStack** (로컬 S3 에뮬레이션)

## 주요 기능

### 1. 파일 업로드 및 검증
- CSV 및 Excel (.xlsx) 파일 업로드 지원
- S3 (또는 LocalStack) 에 파일 저장
- 비동기 파일 검증
- Pre-signed URL을 통한 안전한 파일 다운로드

### 2. Pre-signed URL 기반 안전한 다운로드
- **AWS SDK v2 S3Presigner** 사용으로 임시 서명 URL 생성
- **10분 만료 시간** 설정으로 보안 강화 (설정 가능)
- S3 버킷에 직접 접근하지 않고 안전한 임시 URL 제공
- **동작 방식**:
  1. 클라이언트가 `/files/download/{fileId}` 요청
  2. DB에서 파일 메타데이터 조회
  3. S3Presigner로 서명된 임시 URL 생성
  4. 파일명, 다운로드 URL, 만료 시간 반환
- **보안 고려사항**:
  - URL은 10분 후 자동 만료
  - 현재 인증/인가 없음 (향후 로그인 기능 추가 시 권한 검증 필요)

### 3. 전략 패턴 기반 파일 검증
- 파일 타입별 독립적인 검증 전략
- 확장 가능한 구조 (새로운 파일 타입 추가 용이)
- SOLID 원칙 준수

### 4. 스토리지 추상화 (클라우드 벤더 독립성)
- **StorageService 인터페이스**를 통한 독립적 설계
- AWS S3, Azure Blob Storage, GCP Cloud Storage 등으로 전환 가능
- 서비스 계층은 스토리지 구현체를 알지 못함 (의존성 역전 원칙)
- **확장 방법**:
  - 새로운 클라우드 제공자 추가 시 `StorageService` 구현체만 작성
  - 설정 파일(`application-{provider}.yml`)만으로 전환 가능
  - 비즈니스 로직 수정 불필요
- Mock 객체로 쉽게 단위 테스트 가능

### 5. LocalStack S3 지원
- 로컬 개발 환경에서 AWS S3 에뮬레이션
- 프로덕션과 개발 환경 완전 분리
- 비용 절감 및 오프라인 개발 가능

## 빠른 시작

### 사전 요구사항
- Java 21
- Docker & Docker Compose
- PostgreSQL (로컬 실행 시)

### 로컬 개발 환경 설정

#### 1. LocalStack 시작
```bash
docker-compose up -d
```

#### 2. 데이터베이스 설정
PostgreSQL 데이터베이스를 생성합니다:
```sql
CREATE DATABASE coupon_db;
```

#### 3. 애플리케이션 실행
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

또는 IDE에서 실행 시:
- Program arguments: `--spring.profiles.active=local`

#### 4. API 테스트
```http
POST http://localhost:8080/files/upload
Content-Type: multipart/form-data

file: [CSV 또는 Excel 파일]
```

### 프로덕션 환경 설정

```bash
# 환경변수 설정
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key

# 애플리케이션 실행
./gradlew bootRun
```

## 프로젝트 구조

```
coupon-admin/
├── src/main/java/com/example/coupon_admin/
│   ├── controller/          # REST API 컨트롤러
│   ├── service/             # 비즈니스 로직
│   ├── storage/             # 스토리지 추상화 계층
│   │   ├── StorageService.java          # 스토리지 인터페이스
│   │   └── S3StorageService.java        # AWS S3 구현체
│   ├── validator/           # 파일 검증 전략 패턴
│   │   ├── FileValidatorStrategy.java
│   │   ├── CsvFileValidator.java
│   │   ├── ExcelFileValidator.java
│   │   ├── FileValidatorFactory.java
│   │   └── ValidationResult.java
│   ├── domain/              # 엔티티
│   ├── repository/          # JPA 리포지토리
│   └── global/config/       # 설정 클래스
├── docs/                    # 프로젝트 문서
│   ├── LOCALSTACK_SETUP.md
│   ├── FILE_VALIDATION_STRATEGY.md
│   └── STORAGE_ABSTRACTION.md
├── docker-compose.yml       # LocalStack 설정
└── init-localstack.sh       # LocalStack 초기화 스크립트
```

## API 엔드포인트

### 파일 업로드
```
POST /files/upload
Content-Type: multipart/form-data
Body: file (CSV 또는 Excel)

Response:
{
    "isSuccess": true,
    "code": "COMMON200",
    "message": "성공입니다.",
    "data": {
        "fileId": 55,
        "status": "UPLOADING",
        "message": "파일 업로드가 정상적으로 접수되었습니다. 잠시 후 처리 결과를 확인하세요."
    }
}
```

### 파일 다운로드
```
GET /files/download/{fileId}

Response:
{
    "isSuccess": true,
    "code": "COMMON200",
    "message": "성공입니다.",
    "data": {
        "fileName": "test_excel.xlsx",
        "downloadUrl": "http://localhost:4566/coupon-upload-bucket/a416ab41-c079-467a-8602-8a30e2000573-test_excel.xlsx?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20251115T200215Z&X-Amz-SignedHeaders=host&X-Amz-Credential=test%2F20251115%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Expires=600&X-Amz-Signature=a6151302dd4c2dc5d46cc4d4237d0a3d3b6636c4c36f7fd5bb02ed1f1969ca2c",
        "expiresAt": "2025-11-16T05:12:15.4913155"
    }
}
```

## 환경 설정

### application.yml (프로덕션)
```yaml
cloud:
  aws:
    credentials:
      access-key: ${AWS_ACCESS_KEY_ID:}
      secret-key: ${AWS_SECRET_ACCESS_KEY:}
    s3:
      bucket: coupon-upload-bucket
    region:
      static: ap-northeast-2
```

### application-local.yml (로컬 개발)
```yaml
cloud:
  aws:
    credentials:
      access-key: test
      secret-key: test
    s3:
      bucket: coupon-upload-bucket
      endpoint: http://localhost:4566
    region:
      static: us-east-1
```

## 상세 문서

### 📚 [LocalStack S3 전환 가이드](docs/LOCALSTACK_SETUP.md)
- AWS SDK v1 → v2 마이그레이션 과정
- LocalStack 설정 및 실행 방법
- 트러블슈팅 가이드

### 📚 [파일 검증 전략 패턴 가이드](docs/FILE_VALIDATION_STRATEGY.md)
- 전략 패턴 도입 배경 및 아키텍처
- 구현 세부사항
- 새로운 파일 타입 추가 방법

### 📚 [스토리지 추상화 가이드](docs/STORAGE_ABSTRACTION.md)
- 클라우드 벤더 독립성을 위한 리팩토링
- StorageService 인터페이스 설계
- AWS S3 → Azure/GCP로 전환 방법
- 마이그레이션 체크리스트

## 개발 가이드

### 새로운 파일 타입 추가하기

1. **전략 구현체 생성**
   ```java
   @Component
   public class NewFileValidator implements FileValidatorStrategy {
       @Override
       public ValidationResult validate(InputStream inputStream) {
           // 검증 로직 구현
       }
   }
   ```

2. **팩토리에 전략 등록**
   ```java
   return switch (extension) {
       case "csv" -> csvFileValidator;
       case "xlsx" -> excelFileValidator;
       case "new" -> newFileValidator;  // 추가
       default -> throw new IllegalArgumentException(...);
   };
   ```

### 다른 클라우드 스토리지로 전환하기

현재 AWS S3를 사용 중이지만, 스토리지 추상화 계층 덕분에 Azure Blob Storage, GCP Cloud Storage 등으로 쉽게 전환할 수 있습니다.

1. **새로운 스토리지 구현체 생성**
   ```java
   @Service
   @ConditionalOnProperty(name = "cloud.storage.provider", havingValue = "azure")
   public class AzureBlobStorageService implements StorageService {
       @Override
       public void uploadFile(String key, InputStream content, long size, String contentType) {
           // Azure SDK 사용
       }
       // 나머지 메서드 구현...
   }
   ```

2. **설정 파일 추가** (`application-azure.yml`)
   ```yaml
   cloud:
     storage:
       provider: azure
   azure:
     storage:
       connection-string: ${AZURE_STORAGE_CONNECTION_STRING}
       container: coupon-uploads
   ```

3. **프로파일로 전환**
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=azure'
   ```

자세한 내용은 [스토리지 추상화 가이드](docs/STORAGE_ABSTRACTION.md)를 참고하세요.

## 트러블슈팅

### LocalStack 연결 실패
```bash
# LocalStack 상태 확인
docker logs localstack-s3

# LocalStack 재시작
docker-compose down && docker-compose up -d
```

### S3 버킷 없음 에러
```bash
# 수동으로 버킷 생성
docker exec -it localstack-s3 awslocal s3 mb s3://coupon-upload-bucket --region us-east-1
```

### 순환 참조 에러
- `--spring.profiles.active=local` 옵션 확인
- docs/LOCALSTACK_SETUP.md의 트러블슈팅 섹션 참조