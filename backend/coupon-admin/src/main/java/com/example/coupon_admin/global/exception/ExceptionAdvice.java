package com.example.coupon_admin.global.exception;

import com.example.coupon_admin.global.ApiResponse;
import com.example.coupon_admin.global.ReasonDTO;
import com.example.coupon_admin.global.status.ErrorStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j // 로깅을 위한 Lombok 어노테이션
@RestControllerAdvice(annotations = {RestController.class}) // 모든 @RestController에서 발생하는 예외를 처리
public class ExceptionAdvice extends ResponseEntityExceptionHandler {

    // 1. ConstraintViolationException 처리 (Bean Validation에서 발생하는 예외)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> validation(ConstraintViolationException e, WebRequest request) {
        log.warn("ConstraintViolationException: " + e.getMessage(), e);

        String errorMessage = e.getConstraintViolations().stream()
                .map(cv -> cv.getMessage())
                .findFirst()
                .orElse(ErrorStatus._BAD_REQUEST.getMessage()); // 에러 메시지 추출

        ApiResponse<Object> body = ApiResponse.onFailure(ErrorStatus._BAD_REQUEST.getCode(), errorMessage, null);

        return handleExceptionInternal(e, body, HttpHeaders.EMPTY, ErrorStatus._BAD_REQUEST.getHttpStatus(), request);
    }

    // 2. MethodArgumentNotValidException 처리 (@Valid 어노테이션에서 발생하는 예외)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError -> {
            String fieldName = fieldError.getField();
            String errorMessage = Optional.ofNullable(fieldError.getDefaultMessage()).orElse("");
            errors.merge(fieldName, errorMessage, (existingErrorMessage, newErrorMessage) -> existingErrorMessage + ", " + newErrorMessage);
        });

        log.warn("MethodArgumentNotValidException: " + errors, ex); // 로깅
        ApiResponse<Object> body = ApiResponse.onFailure(ErrorStatus._BAD_REQUEST.getCode(), ErrorStatus._BAD_REQUEST.getMessage(), errors);
        return handleExceptionInternal(ex, body, headers, ErrorStatus._BAD_REQUEST.getHttpStatus(), request);
    }

    // 3. 일반 Exception 처리 (처리되지 않은 모든 예외)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> exception(Exception e, WebRequest request) {
        log.error("Exception: ", e); // 스택 트레이스 로깅
        String errorMessage = "알 수 없는 오류가 발생했습니다."; // 일반적인 오류 메시지
        ApiResponse<Object> body = ApiResponse.onFailure(ErrorStatus._INTERNAL_SERVER_ERROR.getCode(), errorMessage, null);
        return handleExceptionInternal(e, body, HttpHeaders.EMPTY, ErrorStatus._INTERNAL_SERVER_ERROR.getHttpStatus(), request);
    }

    // 4. 사용자 정의 예외(GeneralException) 처리
    @ExceptionHandler(value = GeneralException.class)
    public ResponseEntity<Object> onThrowException(GeneralException generalException, HttpServletRequest request) {
        ReasonDTO errorReasonHttpStatus = generalException.getErrorReasonHttpStatus();
        ApiResponse<Object> body = ApiResponse.onFailure(errorReasonHttpStatus.getCode(), errorReasonHttpStatus.getMessage(), null);
        return handleExceptionInternal(generalException, body, null, errorReasonHttpStatus.getHttpStatus(), new ServletWebRequest(request));
    }

    // 5. handleExceptionInternal 커스텀 메서드 (Spring의 handleExceptionInternal을 확장하여 ApiResponse를 본문으로 사용)
    private ResponseEntity<Object> handleExceptionInternal(Exception e, ApiResponse<Object> body,
                                                           HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        return super.handleExceptionInternal(
                e,
                body,
                headers,
                status,
                request
        );
    }
}