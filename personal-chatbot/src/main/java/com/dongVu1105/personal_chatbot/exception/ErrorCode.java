package com.dongVu1105.personal_chatbot.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

@Getter
public enum ErrorCode {
    UNAUTHENTICATED(1001, "authenticated error", HttpStatus.UNAUTHORIZED),
    USER_EXISTED(1002, "user existed", HttpStatus.BAD_REQUEST),
    USER_NOT_EXISTED(1003, "user not existed", HttpStatus.BAD_REQUEST),
    ROLE_NOT_EXISTED(1004, "role not existed", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(1005, "You do not have permission", HttpStatus.FORBIDDEN),
    UNCATEGORIZED(1006, "uncategorized exception", HttpStatus.BAD_REQUEST)
    ;


    private long code;
    private String message;
    private HttpStatusCode httpStatusCode;

    ErrorCode(long code, String message, HttpStatusCode httpStatusCode) {
        this.code = code;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }
}
