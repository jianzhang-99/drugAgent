package com.liang.drugagent.domain;

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

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
