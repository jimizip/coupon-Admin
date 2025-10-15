package com.example.coupon_admin.controller;

import com.example.coupon_admin.dto.DownloadUrlResponse;
import com.example.coupon_admin.dto.UploadResponse;
import com.example.coupon_admin.global.ApiResponse;
import com.example.coupon_admin.service.FileUploadService;
import com.example.coupon_admin.service.FileDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class FileController {

    private final FileUploadService fileUploadService;
    private final FileDownloadService fileDownloadService;

    // 파일 업로드 API
    @PostMapping("/upload")
    public ApiResponse<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        // 1) 최소 입력 검증: 파일 존재 여부와 파일명
        if (file == null || file.isEmpty()) {
            // GlobalExceptionHandler에서 처리하는 커스텀 예외로 던지는 것을 권장
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw new IllegalArgumentException("파일명이 비어 있습니다.");
        }

        // 2) 서비스 호출: S3 업로드 + 메타데이터 저장 + 비동기 검증 트리거
        Long fileId = fileUploadService.upload(file);

        // 3) 응답 DTO 구성: 업로드 직후 상태는 UPLOADING
        UploadResponse response = UploadResponse.builder()
                .fileId(fileId)
                .status("UPLOADING")
                .message("파일 업로드가 정상적으로 접수되었습니다. 잠시 후 처리 결과를 확인하세요.")
                .build();

        return ApiResponse.onSuccess(response);
    }

    // 파일 다운로드 URL 생성 API
    @GetMapping("/download/{fileId}")
    public ApiResponse<DownloadUrlResponse> getDownloadUrl(@PathVariable("fileId") Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("유효하지 않은 fileId 입니다.");
        }

        // 서비스에서 프리사인드 URL, 만료시각, 파일명 획득
        FileDownloadService.DownloadUrlInfo urlInfo = fileDownloadService.getPresignedDownloadUrl(fileId);

        DownloadUrlResponse response = DownloadUrlResponse.builder()
                .fileName(urlInfo.getFileName())
                .downloadUrl(urlInfo.getDownloadUrl())
                .expiresAt(urlInfo.getExpiresAt())
                .build();

        return ApiResponse.onSuccess(response);
    }
}
