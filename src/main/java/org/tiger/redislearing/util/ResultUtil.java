package org.tiger.redislearing.util;


import lombok.Data;

/**
 * @ClassName Result
 * @Description 返回结果工具类
 * @Author tiger
 * @Date 2025/9/20 16:38
 */
@Data
public class ResultUtil<T> {

    public static final Integer SUCCESS_CODE = 200;
    public static final Integer FAIL_CODE = 400;
    public static final String SUCCESS_MESSAGE = "success";
    public static final String FAIL_MESSAGE = "fail";
    /**
     * 返回状态码
     */
    private Integer code;
    /**
     * 返回信息
     */
    private String message;

    /**
     * 返回数据
     */
    private T data;

    private ResultUtil() {
    }

    public static <T> ResultUtil<T> success() {
        ResultUtil<T> resultUtil = new ResultUtil<>();
        resultUtil.setCode(SUCCESS_CODE);
        resultUtil.setMessage(SUCCESS_MESSAGE);
        return resultUtil;
    }

    public static <T> ResultUtil<T> success(T data) {
        ResultUtil<T> resultUtil = success();
        resultUtil.setData(data);
        return resultUtil;
    }

    public static <T> ResultUtil<T> success(String message, T data) {
        ResultUtil<T> resultUtil = success();
        resultUtil.setMessage(message);
        resultUtil.setData(data);
        return resultUtil;
    }

    public static <T> ResultUtil<T> success(Integer code, String message, T data) {
        ResultUtil<T> resultUtil = new ResultUtil<>();
        resultUtil.setCode(code);
        resultUtil.setMessage(message);
        resultUtil.setData(data);
        return resultUtil;
    }

    public static <T> ResultUtil<T> fail() {
        ResultUtil<T> resultUtil = new ResultUtil<>();
        resultUtil.setCode(FAIL_CODE);
        resultUtil.setMessage(FAIL_MESSAGE);
        return resultUtil;
    }

    public static <T> ResultUtil<T> fail(T data) {
        ResultUtil<T> resultUtil = fail();
        resultUtil.setData(data);
        return resultUtil;
    }

    public static <T> ResultUtil<T> fail(String message, T data) {
        ResultUtil<T> resultUtil = fail();
        resultUtil.setMessage(message);
        resultUtil.setData(data);
        return resultUtil;
    }

    public static <T> ResultUtil<T> fail(Integer code, String message) {
        ResultUtil<T> resultUtil = fail();
        resultUtil.setCode(code);
        resultUtil.setMessage(message);
        return resultUtil;
    }

    public static <T> ResultUtil<T> fail(Integer code, String message, T data) {
        ResultUtil<T> resultUtil = new ResultUtil<>();
        resultUtil.setCode(code);
        resultUtil.setMessage(message);
        resultUtil.setData(data);
        return resultUtil;
    }
}
