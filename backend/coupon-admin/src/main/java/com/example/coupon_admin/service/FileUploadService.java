package com.example.coupon_admin.service;

import com.example.coupon_admin.domain.UploadFile;
import com.example.coupon_admin.repository.UploadFileRepository;
import com.example.coupon_admin.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final StorageService storageService;
    private final UploadFileRepository uploadFileRepository;
    private final FileValidationService fileValidationService;

    public Long upload(MultipartFile multipartFile) throws IOException {
        // 1. S3에 저장할 고유한 파일명 생성
        String s3FileName = UUID.randomUUID() + "-" + multipartFile.getOriginalFilename();

        // 2. 파일 메타데이터를 먼저 DB에 저장
        UploadFile uploadFile = UploadFile.builder()
                .originalFileName(multipartFile.getOriginalFilename())
                .storagePath(s3FileName)
                .fileSize(multipartFile.getSize())
                .build();
        uploadFileRepository.save(uploadFile);

        // 3. 스토리지에 파일 업로드
        storageService.uploadFile(
                s3FileName,
                multipartFile.getInputStream(),
                multipartFile.getSize(),
                multipartFile.getContentType()
        );

        // 4. 비동기 파일 검증 로직 호출
        fileValidationService.validateFile(uploadFile.getId());

        return uploadFile.getId(); // 생성된 파일 ID 반환
    }
}
