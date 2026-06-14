package ru.fcref.system.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.fcref.system.service.BusinessRuleException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException exception) {
        return ResponseEntity
                .status(statusFor(exception.getCode()))
                .body(new ApiError(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception exception) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("BAD_REQUEST", exception.getMessage()));
    }

    private HttpStatus statusFor(String code) {
        if ("ACCESS_DENIED".equals(code)) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.endsWith("_NOT_FOUND") || code.contains("NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        if (code.startsWith("VALIDATION_") || code.startsWith("REGULATION_")) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.CONFLICT;
    }

    public record ApiError(String code, String message) {

        public Map<String, String> asMap() {
            return Map.of("code", code, "message", message);
        }
    }
}
