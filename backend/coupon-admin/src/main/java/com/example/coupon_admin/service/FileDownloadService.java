package com.example.coupon_admin.service;

import com.example.coupon_admin.domain.UploadFile;
import com.example.coupon_admin.repository.UploadFileRepository;
import com.example.coupon_admin.storage.StorageService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class FileDownloadService {

    private final StorageService storageService;
    private final UploadFileRepository uploadFileRepository;

    // application.yml에 설정된 만료 시간(분)을 주입받음
    @Value("${app.s3.presigned-url.expiration-minutes}")
    private long expirationMinutes;

    /**
     * 파일 다운로드에 필요한 정보(URL, 파일명, 만료시각)를 담은 객체를 반환합니다.
     * @param fileId 다운로드할 파일의 ID
     * @return DownloadUrlInfo 객체
     */
    public DownloadUrlInfo getPresignedDownloadUrl(Long fileId) {
        // 1. DB에서 파일 메타데이터 조회
        UploadFile uploadFile = uploadFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("해당 파일을 찾을 수 없습니다. fileId=" + fileId));

        // 2. Pre-signed URL의 만료 시각 설정
        Instant now = Instant.now();
        Instant expirationTime = now.plusSeconds(expirationMinutes * 60);

        // 3. 스토리지 서비스를 통해 Pre-signed URL 생성
        try {
            String presignedUrl = storageService.generatePresignedUrl(
                    uploadFile.getStoragePath(),
                    Duration.ofMinutes(expirationMinutes)
            );

            // 4. 컨트롤러에 전달할 DTO를 생성하여 반환
            return new DownloadUrlInfo(
                    uploadFile.getOriginalFileName(),
                    presignedUrl,
                    LocalDateTime.ofInstant(expirationTime, ZoneId.systemDefault())
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }

    /**
     * 서비스 레이어 내부에서 컨트롤러로 데이터를 전달하기 위한 DTO.
     * 외부로 노출되는 응답 DTO와는 분리하여 내부 구조 변경에 유연하게 대응할 수 있다.
     */
    @Getter
    @AllArgsConstructor
    public static class DownloadUrlInfo {
        private String fileName;
        private String downloadUrl;
        private LocalDateTime expiresAt;
    }
}
