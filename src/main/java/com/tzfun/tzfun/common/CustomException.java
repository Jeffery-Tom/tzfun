package com.tzfun.tzfun.common;

import lombok.Getter;

/**
 * 业务异常，由全局异常处理器统一转成 Result 返回。
 */
@Getter
public class CustomException extends RuntimeException {

    private final String code;

    public CustomException(String code, String msg) {
        super(msg);
        this.code = code;
    }

    public CustomException(String msg) {
        this("500", msg);
    }
}
