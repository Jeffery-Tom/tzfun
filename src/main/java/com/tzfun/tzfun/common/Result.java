package com.tzfun.tzfun.common;

import lombok.Data;

/**
 * 统一返回结构。code 用字符串："200" 成功，其余为错误码。
 */
@Data
public class Result<T> {

    private String code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = "200";
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(String code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
