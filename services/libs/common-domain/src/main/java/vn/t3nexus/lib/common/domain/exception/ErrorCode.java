package vn.t3nexus.lib.common.domain.exception;

public interface ErrorCode {
    String code();
    String defaultMessage();
    String messageKey();
    int httpStatus();
}
