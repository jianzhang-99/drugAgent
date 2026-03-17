package com.liang.drugagent.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 通用接口返回对象。
 *
 * @author liangjiajian
 */
@Setter
@Getter
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public Result() {}

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>(200, "success", data);
    }
    
    public static <T> Result<T> error(String message) {
        return new Result<T>(500, message, null);
    }

}
