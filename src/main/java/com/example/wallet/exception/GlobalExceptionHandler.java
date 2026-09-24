package com.example.wallet.exception;

import com.example.wallet.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WalletNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(WalletNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("WALLET_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler({WalletAlreadyExistsException.class, DataIntegrityViolationException.class})
    ResponseEntity<ErrorResponse> conflict(Exception e) {
        // DataIntegrityViolation: a concurrent request won the race on the unique constraint.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("WALLET_ALREADY_EXISTS", "A wallet already exists for this user"));
    }

    @ExceptionHandler(InvalidRequestException.class)
    ResponseEntity<ErrorResponse> invalid(InvalidRequestException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<ErrorResponse> idempotencyKeyReused(IdempotencyKeyReusedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("IDEMPOTENCY_KEY_REUSED", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyInProgressException.class)
    ResponseEntity<ErrorResponse> idempotencyKeyInProgress(IdempotencyKeyInProgressException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("IDEMPOTENCY_KEY_IN_PROGRESS", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> missingHeader(org.springframework.web.bind.MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    ResponseEntity<ErrorResponse> insufficientBalance(InsufficientBalanceException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorResponse("INSUFFICIENT_BALANCE", e.getMessage()));
    }

    @ExceptionHandler(InvalidOtpException.class)
    ResponseEntity<ErrorResponse> invalidOtp(InvalidOtpException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_OTP", e.getMessage()));
    }

    @ExceptionHandler(WalletNotActiveException.class)
    ResponseEntity<ErrorResponse> walletNotActive(WalletNotActiveException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("WALLET_NOT_ACTIVE", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> badRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", "Invalid request"));
    }

    // Thrown by @Valid on a @RequestBody, e.g. DepositRequest's @NotNull/@Positive amount.
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validationFailed(org.springframework.web.bind.MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST", "Invalid request"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> other(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Internal server error"));
    }
}
