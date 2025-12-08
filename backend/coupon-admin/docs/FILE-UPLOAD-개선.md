# 대용량 파일 업로드 구현 분석 및 개선 방안

## 현재 구현 분석

### 1. Spring Multipart 설정

**파일**: `src/main/resources/application-local.yml:19-23`

```yaml
servlet:
  multipart:
    max-file-size: 200MB      # 개별 파일의 최대 허용 크기
    max-request-size: 210MB   # 전체 요청의 최대 허용 크기
    file-size-threshold: 0    # 모든 파일을 디스크에 임시 저장
```

**설정 의미**:
- `file-size-threshold: 0`: 파일 크기와 관계없이 **모든 파일을 디스크 임시 파일로 처리**
- 이 설정으로 인해 메모리에 파일을 로드하지 않고 디스크 기반으로 처리됨
- Spring의 `StandardMultipartHttpServletRequest`가 자동으로 임시 파일 생성 및 관리

### 2. 파일 업로드 처리 흐름

```
[클라이언트]
    ↓ (HTTP POST /api/files/upload)
[FileController]
    ↓ (MultipartFile - 디스크 임시 파일)
[FileUploadService]
    ↓ (InputStream 전달)
[S3StorageService]
    ↓ (단일 PUT)
[AWS S3]
    ↓ (비동기)
[FileValidationService]
    ↓ (S3에서 재다운로드)
[Validator (CSV/Excel)]
```

---

## 구현된 부분

### 1. 디스크 기반 임시 파일 처리
**설정**: `file-size-threshold: 0`

**장점**:
- 파일 크기와 관계없이 메모리 압박 최소화
- 200MB 대용량 파일도 메모리 사용량이 일정
- Spring이 요청 완료 후 임시 파일 자동 삭제

**동작 원리**:
```
[클라이언트 업로드]
    ↓
[Servlet Container]
    ↓ (file-size-threshold: 0)
[디스크 임시 파일 생성]
    ↓
[MultipartFile로 래핑]
    ↓
[요청 처리]
    ↓
[Spring이 자동으로 임시 파일 삭제]
```

### 2. 스트리밍 방식 파일 업로드
**구현**: `multipartFile.getInputStream()` 사용

**장점**:
- 파일 전체를 메모리에 로드하지 않음
- InputStream을 S3로 직접 전달하여 효율적 처리
- `multipartFile.getBytes()` 대신 스트리밍 사용

**비교**:
```java
// 전체 메모리 로드
byte[] bytes = multipartFile.getBytes();
s3Client.putObject(..., RequestBody.fromBytes(bytes));

// 스트리밍
InputStream stream = multipartFile.getInputStream();
s3Client.putObject(..., RequestBody.fromInputStream(stream, size));
```

### 3. 비동기 파일 검증
**구현**: `@Async` 어노테이션

**장점**:
- 파일 업로드 응답 속도 개선 (즉시 응답)
- 검증은 백그라운드에서 수행
- 사용자 경험 향상

**처리 흐름**:
```
[업로드 요청] → [S3 업로드] → [즉시 응답] → [ID 반환]
                                    ↓
                          [백그라운드 검증]
                                    ↓
                          [DB 상태 업데이트]
```

### 4. CSV 스트리밍 검증
**구현**: `BufferedReader`로 라인별 읽기

**장점**:
- 파일 크기와 무관하게 일정한 메모리 사용
- 200MB CSV 파일도 안전하게 검증 가능
- 헤더와 첫 데이터 라인만 읽어 빠른 검증

---

## 개선이 필요한 부분

### 1. AWS S3 Multipart Upload 미사용

#### 문제점
**파일**: `src/main/java/com/example/coupon_admin/storage/S3StorageService.java`

현재 구현은 파일 크기와 관계없이 단일 `putObject()` 호출을 사용합니다.

```java
// 현재 구현 (단일 PUT)
s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(content, size));
```

**문제**:
1. **업로드 실패 시 전체 재전송**
   - 200MB 파일 업로드 중 네트워크 끊김 발생 시
   - 처음부터 다시 업로드해야 함 (재개 불가)

2. **느린 업로드 속도**
   - 단일 스레드로 순차 업로드
   - 대역폭 활용도 낮음

3. **5GB 크기 제한**
   - 단일 PUT은 최대 5GB까지만 지원
   - 향후 확장성 제약

4. **긴 HTTP 요청 시간**
   - 200MB 업로드 완료까지 커넥션 유지
   - 타임아웃 위험

#### AWS 권장사항
- **100MB 이상**: Multipart Upload 사용 권장
- **5GB 이상**: Multipart Upload 필수

#### 개선 방안

**AWS S3 Multipart Upload API 사용**:

**또는 AWS Transfer Manager 사용** (더 간단):

---

### 2. InputStream 리소스 누수 위험

#### 문제점
**파일**:
- `src/main/java/com/example/coupon_admin/service/FileUploadService.java`
- `src/main/java/com/example/coupon_admin/storage/S3StorageService.java`

```java
// FileUploadService.java
storageService.uploadFile(
    s3FileName,
    multipartFile.getInputStream(),  // ⚠️ InputStream 생성
    multipartFile.getSize(),
    multipartFile.getContentType()
);
// close() 호출 없음

// S3StorageService.java
public void uploadFile(String key, InputStream content, long size, String contentType)
        throws IOException {
    s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(content, size));
    // content InputStream을 close하지 않음
}
```

**문제**:
1. **파일 디스크립터 누수**
   - InputStream이 명시적으로 close되지 않음
   - 운영체제의 파일 디스크립터 고갈 가능성

2. **임시 파일 삭제 지연**
   - InputStream이 열려있으면 임시 파일 삭제가 지연될 수 있음
   - 디스크 공간 부족 가능성

3. **가비지 컬렉션 의존**
   - JVM GC가 finalize()를 호출할 때까지 리소스 유지
   - 예측 불가능한 리소스 해제 시점

#### 개선 방안

**try-with-resources 패턴 사용**:

**또는 파일 경로 기반 업로드**:

---

## 📚 참고 자료

### AWS 공식 문서
- [S3 Multipart Upload](https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html)
- [AWS Transfer Manager](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/transfer-manager.html)

### Apache POI 문서
- [SXSSF (Streaming Workbook)](https://poi.apache.org/components/spreadsheet/how-to.html#sxssf)
- [Event API (SAX)](https://poi.apache.org/components/spreadsheet/how-to.html#event_api)

### Spring Framework
- [Multipart File Upload](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/multipart-forms.html)
- [Commons FileUpload Configuration](https://commons.apache.org/proper/commons-fileupload/using.html)

### 추가 참고
- [우아한형제들 기술 블로그 - 대용량 파일 업로드](https://techblog.woowahan.com/11392/) (원본 자료)
- [Excel Streaming Reader GitHub](https://github.com/pjfanning/excel-streaming-reader)