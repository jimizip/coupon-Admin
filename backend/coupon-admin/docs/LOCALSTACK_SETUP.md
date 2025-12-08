# LocalStack S3 전환 가이드

## 목차
- [전환 배경](#전환-배경)
- [주요 변경 사항](#주요-변경-사항)
- [설정 파일 구조](#설정-파일-구조)
- [실행 방법](#실행-방법)
- [트러블슈팅](#트러블슈팅)

---

## 전환 배경

### 목적
- **비용 절감**: 로컬 개발 환경에서 실제 AWS S3 비용 발생 방지
- **개발 편의성**: 인터넷 연결 없이 S3 기능 테스트 가능
- **환경 분리**: 개발/테스트 환경과 프로덕션 환경 완전 분리

### LocalStack이란?
AWS 클라우드 서비스를 로컬에서 에뮬레이트하는 오픈소스 도구입니다. S3, DynamoDB, Lambda 등 다양한 AWS 서비스를 로컬 Docker 컨테이너에서 실행할 수 있습니다.

---

## 주요 변경 사항

### 1. AWS SDK 업그레이드
**v1 → v2 마이그레이션**

#### build.gradle
```gradle
// 변경 전 (AWS SDK v1)
implementation 'com.amazonaws:aws-java-sdk-s3:1.12.308'

// 변경 후 (AWS SDK v2)
implementation platform('software.amazon.awssdk:bom:2.25.11')
implementation 'software.amazon.awssdk:s3'
```

#### S3Config.java
```java
// 변경 전 (v1)
@Bean
public AmazonS3Client amazonS3Client() {
    BasicAWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
    return (AmazonS3Client) AmazonS3ClientBuilder.standard()
            .withRegion(region)
            .withCredentials(new AWSStaticCredentialsProvider(credentials))
            .build();
}

// 변경 후 (v2)
@Bean
public S3Client s3Client() {
    AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
    S3ClientBuilder builder = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials));

    // LocalStack endpoint 설정
    if (endpoint != null && !endpoint.isEmpty()) {
        builder.endpointOverride(URI.create(endpoint))
               .serviceConfiguration(S3Configuration.builder()
                       .pathStyleAccessEnabled(true)  // LocalStack은 path-style 필요
                       .build());
    }

    return builder.build();
}
```

### 2. 서비스 레이어 API 변경

#### FileUploadService.java
```java
// 변경 전 (v1)
ObjectMetadata objMeta = new ObjectMetadata();
objMeta.setContentLength(multipartFile.getInputStream().available());
amazonS3Client.putObject(bucket, s3FileName, multipartFile.getInputStream(), objMeta);

// 변경 후 (v2)
PutObjectRequest putObjectRequest = PutObjectRequest.builder()
        .bucket(bucket)
        .key(s3FileName)
        .contentType(multipartFile.getContentType())
        .contentLength(multipartFile.getSize())
        .build();

s3Client.putObject(putObjectRequest,
        RequestBody.fromInputStream(multipartFile.getInputStream(), multipartFile.getSize()));
```

#### FileDownloadService.java
```java
// 변경 전 (v1)
GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, uploadFile.getS3FilePath())
        .withMethod(HttpMethod.GET)
        .withExpiration(expiration);
String presignedUrl = amazonS3Client.generatePresignedUrl(request).toString();

// 변경 후 (v2)
GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(bucket)
        .key(uploadFile.getS3FilePath())
        .build();

GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(Duration.ofMinutes(expirationMinutes))
        .getObjectRequest(getObjectRequest)
        .build();

PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
String presignedUrl = presignedRequest.url().toString();
```

#### FileValidationService.java
```java
// 변경 전 (v1)
S3Object s3Object = amazonS3Client.getObject(bucket, uploadFile.getS3FilePath());
try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3Object.getObjectContent()))) {
    // ...
}

// 변경 후 (v2)
GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(bucket)
        .key(uploadFile.getS3FilePath())
        .build();

try (ResponseInputStream<GetObjectResponse> s3ObjectInputStream = s3Client.getObject(getObjectRequest)) {
    // ...
}
```

---

## 설정 파일 구조

### Profile 기반 환경 분리

#### application.yml (프로덕션 환경)
```yaml
cloud:
  aws:
    credentials:
      access-key: ${AWS_ACCESS_KEY_ID:}      # 환경변수 사용
      secret-key: ${AWS_SECRET_ACCESS_KEY:}  # 환경변수 사용
    s3:
      bucket: coupon-upload-bucket
    region:
      static: ap-northeast-2  # 서울 리전
```

#### application-local.yml (로컬 개발 환경)
```yaml
cloud:
  aws:
    credentials:
      access-key: test       # LocalStack 더미 credentials
      secret-key: test       # LocalStack 더미 credentials
    s3:
      bucket: coupon-upload-bucket
      endpoint: http://localhost:4566  # LocalStack endpoint
    region:
      static: us-east-1      # LocalStack은 us-east-1 권장
    stack:
      auto: false            # Spring Cloud AWS auto-configuration 비활성화
```

### Docker Compose 설정

#### docker-compose.yml
```yaml
version: '3.8'

services:
  localstack:
    image: localstack/localstack:latest
    container_name: localstack-s3
    ports:
      - "4566:4566"  # LocalStack gateway
    environment:
      - SERVICES=s3  # S3 서비스만 활성화
      - DEBUG=1      # 디버그 로그 활성화
    volumes:
      - "./init-localstack.sh:/etc/localstack/init/ready.d/init-aws.sh"
    networks:
      - app-network

networks:
  app-network:
    driver: bridge
```

#### init-localstack.sh (버킷 자동 생성 스크립트)
```bash
#!/bin/bash

echo "Initializing LocalStack S3..."

# AWS CLI를 사용하여 S3 버킷 생성
awslocal s3 mb s3://coupon-upload-bucket --region us-east-1

# 버킷 목록 확인
awslocal s3 ls

echo "LocalStack S3 initialization complete!"
```

---

## 실행 방법

### 1. LocalStack 시작

```powershell
# Docker Compose로 LocalStack 시작
docker-compose up -d

# 로그 확인
docker logs -f localstack-s3

# 정상 실행 시 "Ready." 메시지 표시
```

### 2. S3 버킷 확인

```powershell
# LocalStack에 버킷이 생성되었는지 확인
docker exec -it localstack-s3 awslocal s3 ls

# 출력: coupon-upload-bucket
```

### 3. Spring Boot 애플리케이션 실행

#### Gradle 명령어
```powershell
./gradlew bootRun --args='--spring.profiles.active=local'
```

#### IDE 설정 (IntelliJ IDEA)
1. Run/Debug Configurations 열기
2. Program arguments에 추가:
   ```
   --spring.profiles.active=local
   ```
   또는 VM options에 추가:
   ```
   -Dspring.profiles.active=local
   ```

### 4. 파일 업로드 테스트

```
POST http://localhost:8080/files/upload
Content-Type: multipart/form-data

file: [CSV 또는 Excel 파일 선택]
```

### 5. LocalStack 종료

```powershell
docker-compose down

# 볼륨까지 삭제
docker-compose down -v
```

---

## 트러블슈팅

### 1. `Device or resource busy` 에러

**증상:**
```
ERROR: 'rm -rf "/tmp/localstack"': exit code 1
OSError: [Errno 16] Device or resource busy: '/tmp/localstack'
```

**원인:** 볼륨 마운트 경로 충돌

**해결:**
- docker-compose.yml에서 `DATA_DIR` 환경변수와 `localstack-data` 볼륨 마운트 제거
- 최소한의 설정만 유지

### 2. 순환 참조 에러

**증상:**
```
BeanCurrentlyInCreationException: Error creating bean with name 's3Config'
```

**원인:** S3Config에서 `@Autowired S3Client`와 `@Bean S3Client` 동시 사용

**해결:**
- `@Autowired private S3Client s3Client;` 필드 제거
- `@EventListener` 자동 버킷 생성 로직 제거
- 수동으로 버킷 생성하거나 init 스크립트 사용

### 3. NoSuchBucketException

**증상:**
```
NoSuchBucketException: The specified bucket does not exist
```

**원인:** LocalStack에 버킷이 생성되지 않음

**해결:**
```powershell
# 수동으로 버킷 생성
docker exec -it localstack-s3 awslocal s3 mb s3://coupon-upload-bucket --region us-east-1

# 또는 init-localstack.sh 스크립트 확인 (줄바꿈 문제, 실행 권한 등)
```

### 4. Region 불일치

**증상:** 버킷 생성은 되는데 접근이 안 됨

**원인:** LocalStack은 기본적으로 `us-east-1` 사용

**해결:**
- `application-local.yml`의 region을 `us-east-1`로 변경
- 또는 버킷 생성 시 region 명시

### 5. Profile 미지정

**증상:** 애플리케이션 시작 시 AWS credentials 에러

**원인:** `--spring.profiles.active=local` 없이 실행

**해결:**
- 반드시 `local` profile로 실행
- 프로덕션 환경에서는 환경변수로 AWS credentials 설정

---

## 환경별 실행 정리

### 로컬 개발 (LocalStack)
```powershell
# 1. LocalStack 시작
docker-compose up -d

# 2. 애플리케이션 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 프로덕션 (실제 AWS S3)
```powershell
# 1. 환경변수 설정
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key

# 2. 애플리케이션 실행 (기본 profile)
./gradlew bootRun
```

---

## 참고 자료

- [LocalStack 공식 문서](https://docs.localstack.cloud/)
- [AWS SDK for Java v2 마이그레이션 가이드](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/migration.html)
- [Spring Cloud AWS](https://spring.io/projects/spring-cloud-aws)
