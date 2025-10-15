package com.example.coupon_admin.dto;

import com.example.coupon_admin.domain.UploadFile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {

    private Long fileId;
    private String status;
    private String message;

    public static UploadResponse fromEntity(UploadFile file) {
        return UploadResponse.builder()
                .fileId(file.getId())
                .status(file.getStatus().name())
                .message("파일 업로드가 정상적으로 접수되었습니다. 잠시 후 처리 결과를 확인하세요.")
                .build();
    }
}
