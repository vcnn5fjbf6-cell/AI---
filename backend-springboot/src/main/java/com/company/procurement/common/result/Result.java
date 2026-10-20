package com.company.procurement.common.result;

import com.company.procurement.common.i18n.ErrorMessageLocalizer;
import lombok.Data;

@Data
public class Result<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<>();
        result.success = true;
        result.code = "0";
        result.message = "success";
        result.data = data;
        return result;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(String code, String message) {
        Result<T> result = new Result<>();
        result.success = false;
        result.code = code;
        result.message = ErrorMessageLocalizer.localize(code, message);
        return result;
    }
}
