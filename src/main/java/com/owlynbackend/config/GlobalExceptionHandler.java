package com.owlynbackend.config;


import com.owlynbackend.internal.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistException.class)
    public ResponseEntity<Map<String, String>> handleUserExists(UserAlreadyExistException ex) {
        log.warn("Handling UserAlreadyExistException: {}", ex.getMessage());
        ResponseEntity<Map<String, String>> response = ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage())); // Returns 409
        log.info("Sent error response to users: {} {}", response.getStatusCode(), response.getBody());
        return response;
    }

    @ExceptionHandler(InvalidOtpException.class)
    public ResponseEntity<Map<String, String>> handleInvalidOtp(InvalidOtpException ex) {
        log.warn("Handling InvalidOtpException: {}", ex.getMessage());
        ResponseEntity<Map<String, String>> response = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage())); // Returns 401
        log.info("Sent error responses to user: {} {}", response.getStatusCode(), response.getBody());
        return response;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException ex) {
        log.warn("Handling InvalidCredentialsException: {}", ex.getMessage());
        ResponseEntity<Map<String, String>> response = ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage())); // Returns 401
        log.info("Sent errors response to user: {} {}", response.getStatusCode(), response.getBody());
        return response;
    }

    // Optional: Catch any remaining raw RuntimeExceptions as a fallback 500
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleGenericException(RuntimeException ex) {
        log.error("Handling RuntimeException: ", ex);
        ResponseEntity<Map<String, String>> response = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "An internal server error occurred.")); // Returns 500
        log.info("Sent error response to user: {} {}", response.getStatusCode(), response.getBody());
        return response;
    }
    // Add inside GlobalExceptionHandler.java

    @ExceptionHandler(InvalidAccessCodeException.class)
    public ResponseEntity<Map<String, String>> handleInvalidAccessCode(InvalidAccessCodeException ex) {
        log.warn("Handling InvalidAccessCodeException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage())); // Returns 404
    }

    @ExceptionHandler(InterviewAlreadyActiveException.class)
    public ResponseEntity<Map<String, String>> handleInterviewAlreadyActive(InterviewAlreadyActiveException ex) {
        log.warn("Handling InterviewAlreadyActiveException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage())); // Returns 409
    }

    @ExceptionHandler(WorkspaceAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleWorkspaceAccessDenied(WorkspaceAccessDeniedException ex) {
        log.warn("Handling WorkspaceAccessDeniedException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage())); // Returns 403
    }

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleWorkspaceNotFound(WorkspaceNotFoundException ex) {
        log.warn("Handling WorkspaceNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage())); // Returns 404
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(InvalidRequestException ex) {
        log.warn("Handling InvalidRequestException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage())); // Returns 400
    }

        @ExceptionHandler(ReportNotReadyException.class)
        public ResponseEntity<Map<String, String>> handleReportNotReady(ReportNotReadyException ex) {
        log.warn("Handling ReportNotReadyException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of(
                "code", "REPORT_NOT_READY",
                "message", ex.getMessage(),
                "error", ex.getMessage()
            ));
        }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("Handling MaxUploadSizeExceededException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "File size exceeds the maximum allowed limit of 7MB."));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipartException(MultipartException ex) {
        log.warn("Handling MultipartException: {}", ex.getMessage());
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (message.contains("maximum upload size") || message.contains("maxuploadsizeexceeded")) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("error", "File size exceeds the maximum allowed limit of 7MB."));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid multipart request."));
    }

    @ExceptionHandler(WorkspaceMemberNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleWorkspaceMemberNotFound(WorkspaceMemberNotFoundException ex) {
        log.warn("Handling WorkspaceMemberNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage())); // Returns 404
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException ex) {
        log.warn("Handling UserNotFoundException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage())); // Returns 404
    }

    @ExceptionHandler(DocumentExtractionException.class)
    public ResponseEntity<Map<String, String>> handleDocumentExtraction(DocumentExtractionException ex) {
        log.error("Handling DocumentExtractionException: ", ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage())); // Returns 400
    }

    @ExceptionHandler(UnauthorizedWebSocketException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorizedWebSocket(UnauthorizedWebSocketException ex) {
        log.warn("Handling UnauthorizedWebSocketException: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage())); // Returns 401
    }


}