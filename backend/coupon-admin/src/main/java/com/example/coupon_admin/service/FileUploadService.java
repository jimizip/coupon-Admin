package com.example.coupon_admin.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.example.coupon_admin.domain.UploadFile;
import com.example.coupon_admin.repository.UploadFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileUploadService {

    private final AmazonS3Client amazonS3Client;
    private final UploadFileRepository uploadFileRepository;
    private final FileValidationService fileValidationService;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public Long upload(MultipartFile multipartFile) throws IOException {
        // 1. S3에 저장할 고유한 파일명 생성
        String s3FileName = UUID.randomUUID() + "-" + multipartFile.getOriginalFilename();

        // 2. 파일 메타데이터를 먼저 DB에 저장
        UploadFile uploadFile = UploadFile.builder()
                .originalFileName(multipartFile.getOriginalFilename())
                .s3FilePath(s3FileName)
                .fileSize(multipartFile.getSize())
                .build();
        uploadFileRepository.save(uploadFile);

        // 3. S3에 파일 업로드
        ObjectMetadata objMeta = new ObjectMetadata();
        objMeta.setContentLength(multipartFile.getInputStream().available());
        amazonS3Client.putObject(bucket, s3FileName, multipartFile.getInputStream(), objMeta);

        // 4. 비동기 파일 검증 로직 호출
        fileValidationService.validateFile(uploadFile.getId());

        return uploadFile.getId(); // 생성된 파일 ID 반환
    }
}
