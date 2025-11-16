package com.example.coupon_admin.storage;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

// 클라우드 스토리지 서비스 추상화 인터페이스
public interface StorageService {

    /**
     * 파일을 스토리지에 업로드합니다.
     *
     * @param key 스토리지 내 파일 경로/키
     * @param content 파일 내용 스트림
     * @param size 파일 크기 (bytes)
     * @param contentType 파일 MIME 타입
     * @throws IOException 업로드 실패 시
     */
    void uploadFile(String key, InputStream content, long size, String contentType) throws IOException;

    /**
     * 스토리지에서 파일을 다운로드합니다.
     *
     * @param key 스토리지 내 파일 경로/키
     * @return 파일 내용 스트림
     * @throws IOException 다운로드 실패 시
     */
    InputStream downloadFile(String key) throws IOException;

    /**
     * 파일 다운로드를 위한 임시 서명된 URL을 생성합니다.
     *
     * @param key 스토리지 내 파일 경로/키
     * @param expiration URL 만료 시간
     * @return 서명된 다운로드 URL
     * @throws IOException URL 생성 실패 시
     */
    String generatePresignedUrl(String key, Duration expiration) throws IOException;
}
