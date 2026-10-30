package dev.travelpilot.common.error;

import org.springframework.http.HttpStatus;

/** Stable error codes returned in the {@code code} field of every ProblemDetail. */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ."),
    INPUT_TOO_LONG(HttpStatus.BAD_REQUEST, "Nội dung quá dài."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Phiên làm việc đã hết hạn, vui lòng tải lại trang."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Bạn gửi quá nhiều yêu cầu, vui lòng thử lại sau."),
    LLM_ERROR(HttpStatus.BAD_GATEWAY, "Dịch vụ AI đang gặp lỗi, vui lòng thử lại."),
    LLM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "Dịch vụ AI phản hồi quá lâu, vui lòng thử lại."),
    PLAN_INVALID_AFTER_REPAIR(HttpStatus.INTERNAL_SERVER_ERROR, "Không tạo được lịch trình hợp lệ."),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "Đã có lỗi xảy ra.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
