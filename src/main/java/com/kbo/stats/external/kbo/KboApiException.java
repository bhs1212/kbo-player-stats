package com.kbo.stats.external.kbo;

public class KboApiException extends RuntimeException {
    public KboApiException(String message) {
        super(message);
    }
    public KboApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
