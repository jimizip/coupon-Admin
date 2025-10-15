package com.example.coupon_admin.dto;

import com.example.coupon_admin.domain.UploadFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DownloadUrlResponse {

    private String fileName;
    private String downloadUrl;
    private LocalDateTime expiresAt; // URL 만료 시각

    public static DownloadUrlResponse of(String fileName, String downloadUrl, LocalDateTime expiresAt) {
        return DownloadUrlResponse.builder()
                .fileName(fileName)
                .downloadUrl(downloadUrl)
                .expiresAt(expiresAt)
                .build();
    }
}
