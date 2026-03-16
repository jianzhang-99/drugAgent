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
public class GeneralResponse<T> {
    private int code;
    private String message;
    private T data;

    public GeneralResponse() {}

    public GeneralResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> GeneralResponse<T> success(T data) {
        return new GeneralResponse<T>(200, "success", data);
    }
    
    public static <T> GeneralResponse<T> error(String message) {
        return new GeneralResponse<T>(500, message, null);
    }

}
