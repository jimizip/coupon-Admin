# 📄 쿠폰 시스템 어드민 - 대용량 파일 처리 백엔드 시스템

## 1. 프로젝트 개요

이 프로젝트는 쿠폰 시스템 어드민의 **대량 사용자 목록(CSV/Excel) 업로드 기능**을 구현한 백엔드 시스템입니다. 최대 200MB에 달하는 대용량 파일을 안정적으로 처리하고, 클라우드 환경에 최적화된 아키텍처를 적용하는 것을 목표로 합니다.

주요 기능은 다음과 같습니다.

-   **대용량 파일 업로드 API**: 사용자가 올린 CSV/Excel 파일을 S3에 안전하게 저장합니다.
-   **비동기 파일 검증**: 파일의 유효성(헤더, 빈 파일 여부)을 백그라운드에서 비동기적으로 처리합니다.
-   **파일 다운로드 API**: 사용자가 디버깅 목적으로 업로드했던 원본 파일을 다시 다운로드할 수 있는 기능을 제공합니다.

## 2. 시스템 아키텍처

이 시스템은 **안정성, 확장성, 그리고 클라우드 친화성**을 최우선으로 고려하여 **스프링 부트와 AWS 서비스를 결합한 하이브리드 아키텍처**를 채택했습니다.

### 아키텍처 다이어그램

```
sequenceDiagram
    participant Client as 사용자 (브라우저)
    participant SpringBoot as 스프링 부트 서버 (EC2)
    participant S3 as AWS S3 (파일 스토리지)
    participant PostgreSQL as 데이터베이스 (RDS)
    participant Async as 비동기 스레드 풀

    Client->>+SpringBoot: 1. 파일 업로드 요청 (POST /files/upload)
    SpringBoot->>+S3: 2. 원본 파일 즉시 업로드
    S3-->>-SpringBoot: 3. 업로드 완료 (S3 경로 반환)
    SpringBoot->>+PostgreSQL: 4. 파일 메타데이터 저장 (상태: UPLOADING)
    PostgreSQL-->>-SpringBoot: 5. 저장 완료 (fileId 반환)
    SpringBoot-->>Client: 6. 업로드 접수 완료 응답 (fileId 포함)
    SpringBoot->>Async: 7. 비동기 검증 작업 시작
    Async->>+S3: 8. S3에서 파일 스트리밍
    S3-->>-Async: 9. 파일 내용 전달
    Async->>+PostgreSQL: 10. 검증 결과 업데이트 (상태: COMPLETED/FAILED)
    PostgreSQL-->>-Async: 11. 업데이트 완료
```

### 기술 선택 및 이유

| 기술 스택        | 선택 이유                                                                                                                                                                                                                           |
| :--------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Spring Boot**  | 성숙하고 안정적인 프레임워크로, 강력한 웹 애플리케이션 및 API 서버를 빠르게 구축할 수 있습니다. 방대한 커뮤니티와 참고 자료를 통해 생산성이 높습니다.                                                                                 |
| **AWS S3**       | **대용량 파일 처리의 핵심**입니다. 200MB 파일을 서버 메모리나 디스크에서 직접 다루는 것은 매우 위험합니다. 파일을 즉시 S3로 넘겨 처리를 위임함으로써 서버의 부하를 최소화하고, 안정적인 파일 저장 및 관리가 가능합니다.               |
| **PostgreSQL**   | 파일 메타데이터(상태, 경로 등)는 구조가 명확하고 데이터의 **정합성**이 중요합니다. ACID 트랜잭션을 보장하는 RDBMS는 파일 상태가 변경되는 과정에서 데이터 무결성을 지켜주는 가장 안정적인 선택입니다.                               |
| **Spring @Async**| 파일 검증은 시간이 걸릴 수 있는 작업이므로, 사용자에게 즉시 응답을 주기 위해 **비동기 처리**가 필수적입니다. `@Async`는 별도 인프라 없이 스프링 부트 내에서 스레드 풀을 이용해 간편하게 비동기 로직을 구현할 수 있어 초기 개발 속도를 높이는 데 유리합니다. |

> #### 💡 **대안에 대한 고민: 왜 메시지 큐를 사용하지 않았나?**
>
> `@Async` 대신 AWS SQS나 RabbitMQ 같은 메시지 큐를 사용하는 방법도 고려했습니다. 메시지 큐는 서버 장애 시 작업 유실 방지, 독립적인 확장성 등에서 더 큰 장점을 가집니다.
>
> 하지만 이 프로젝트의 규모와 요구사항을 고려했을 때, 외부 메시지 큐 인프라를 추가하는 것은 초기 개발 복잡도를 높이는 **오버 엔지니어링(Over-engineering)** 이라 판단했습니다. 우선 `@Async`로 비동기 처리의 핵심 흐름을 구현하고, 향후 트래픽 증가 및 안정성 요구가 높아질 때 최소한의 코드 변경으로 메시지 큐 아키텍처로 전환할 수 있도록 **느슨하게 결합된 구조**로 설계했습니다.

---

## 3. API 명세

자세한 API 명세는 [`API.md`](API.md) 파일을 참고해 주세요. (별도 파일로 관리 권장)

| Endpoint                 | Method | 설명                                                     |
| :----------------------- | :----- | :------------------------------------------------------- |
| `/files/upload`            | `POST` | CSV/Excel 파일을 업로드하고 비동기 검증을 시작합니다.      |
| `/files/download/{fileId}` | `GET`  | 업로드된 파일의 임시 다운로드 URL을 생성합니다.            |

---

## 4. 데이터베이스 스키마 (ERD)

파일 메타데이터를 관리하기 위한 `upload_file` 테이블 하나만 사용합니다.

```
Table: upload_file {
  id               BIGINT [pk, increment]
  original_file_name VARCHAR(255) [not null]
  s3_file_path       VARCHAR(512) [not null, unique]
  file_size          BIGINT [not null]
  status             VARCHAR(32) [not null, note: 'UPLOADING, COMPLETED, FAILED']
  failure_reason   VARCHAR(512)
  created_at       TIMESTAMP
  updated_at       TIMESTAMP
}
```

---

## 5. 실행 및 테스트 방법

### 5.1. 사전 준비

-   **Java 17** 이상, **Gradle** 설치
-   **PostgreSQL** 설치 및 실행
-   **AWS 계정** 및 **S3 버킷** 생성, **IAM 사용자** (Access Key, Secret Key) 발급

### 5.2. 로컬 실행

1.  **DB 생성 및 권한 설정**
    ```
    -- psql -U postgres
    CREATE DATABASE coupon_db;
    CREATE USER coupon_user WITH ENCRYPTED PASSWORD 'password';
    GRANT ALL PRIVILEGES ON DATABASE coupon_db TO coupon_user;
    -- PostgreSQL 15+ 환경에서 public 스키마 권한 문제를 해결하기 위해 아래 명령어를 추가합니다.
    GRANT CREATE ON SCHEMA public TO coupon_user;
    ```

2.  **`application-local.yml` 파일 생성**
    `src/main/resources/` 경로에 `application-local.yml` 파일을 만들고 아래 내용을 채웁니다.

    ```
    spring:
      datasource:
        url: jdbc:postgresql://localhost:5432/coupon_db
        username: coupon_user
        password: password
    
    cloud:
      aws:
        credentials:
          access-key: YOUR_AWS_ACCESS_KEY
          secret-key: YOUR_AWS_SECRET_KEY
        s3:
          bucket: your-s3-bucket-name
    ```

    > **중요**: `.gitignore`에 `application-local.yml`을 추가하여 민감 정보가 유출되지 않도록 합니다.

3.  **애플리케이션 실행**
    ```
    ./gradlew bootRun
    ```

### 5.3. API 테스트

**Postman**과 같은 API 테스트 도구를 사용하여 아래 시나리오를 테스트합니다.

1.  **파일 업로드**: `POST http://localhost:8080/files/upload`
    -   **Body**: `form-data`, `key`는 `file`, `value`는 테스트용 CSV/Excel 파일 첨부
    -   **응답**: `200 OK`와 함께 `fileId`가 포함된 JSON 응답 확인

2.  **파일 다운로드**: `GET http://localhost:8080/files/download/{fileId}`
    -   **경로**: 위에서 받은 `fileId`를 경로에 포함
    -   **응답**: `downloadUrl`이 포함된 JSON 응답 확인 후, 해당 URL로 파일 다운로드 테스트

---

## 6. 향후 개선 과제

-   [ ] **메시지 큐 도입**: `@Async`를 AWS SQS로 전환하여 비동기 처리의 안정성과 확장성 강화
-   [ ] **파일 목록 조회 API**: 관리자 페이지를 위한 파일 업로드 이력 조회 및 페이징 기능 추가
-   [ ] **단위/통합 테스트 코드 강화**: `MockMvc`와 `Testcontainers`를 활용하여 테스트 커버리지 확보
-   [ ] **컨테이너화**: `Dockerfile`을 작성하여 애플리케이션을 컨테이너 이미지로 빌드하고, 배포 용이성 증대
```
