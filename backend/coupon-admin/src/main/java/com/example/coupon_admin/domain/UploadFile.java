package com.example.coupon_admin.domain;

import lombok.*;
import jakarta.persistence.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalFileName; // 사용자가 업로드한 원본 파일명
    private String storagePath;      // 스토리지에 저장된 파일 경로 (key)
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    private FileStatus status;       // 처리 상태 [UPLOADING, COMPLETED, FAILED]

    private String failureReason;    // 실패 시 사유

    @Builder
    public UploadFile(String originalFileName, String storagePath, Long fileSize) {
        this.originalFileName = originalFileName;
        this.storagePath = storagePath;
        this.fileSize = fileSize;
        this.status = FileStatus.UPLOADING; // 최초 상태는 '업로드 중'
    }

    public void complete() {
        this.status = FileStatus.COMPLETED;
    }

    public void fail(String reason) {
        this.status = FileStatus.FAILED;
        this.failureReason = reason;
    }
}