package io.releasehub.common.response;

import io.releasehub.common.paging.PageMeta;

public class ApiPageResponse<T> {
    private boolean success;
    private String code;
    private String message;
    private T data;
    private PageMeta page;

    public ApiPageResponse() {}

    public ApiPageResponse(boolean success, String code, String message, T data, PageMeta page) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.page = page;
    }

    public static <T> ApiPageResponse<T> success(T data, PageMeta page) {
        return new ApiPageResponse<>(true, "OK", "OK", data, page);
    }

    public static <T> ApiPageResponse<T> error(String code, String message) {
        return new ApiPageResponse<>(false, code, message, null, null);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
    public PageMeta getPage() { return page; }
    public void setPage(PageMeta page) { this.page = page; }
}

