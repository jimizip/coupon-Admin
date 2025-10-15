package com.example.coupon_admin.service;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.example.coupon_admin.domain.UploadFile;
import com.example.coupon_admin.repository.UploadFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Service
@RequiredArgsConstructor
public class FileValidationService {

    private final AmazonS3Client amazonS3Client;
    private final UploadFileRepository uploadFileRepository;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Async // 비동기
    @Transactional
    public void validateFile(Long fileId) {
        UploadFile uploadFile = uploadFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        S3Object s3Object = amazonS3Client.getObject(bucket, uploadFile.getS3FilePath());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(s3Object.getObjectContent()))) {
            // 1. 헤더 검증
            String header = reader.readLine();
            if (header == null || !header.trim().equals("customer_id")) {
                uploadFile.fail("Invalid header. Expected 'customer_id'.");
                return;
            }

            // 2. 내용 비어있는지 검증
            if (reader.readLine() == null) {
                uploadFile.fail("File is empty.");
                return;
            }

            // 모든 검증 통과
            uploadFile.complete();

        } catch (IOException e) {
            uploadFile.fail("Error while reading file: " + e.getMessage());
        }
    }
}
