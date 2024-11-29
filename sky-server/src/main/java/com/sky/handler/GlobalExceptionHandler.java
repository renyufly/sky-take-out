package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());

        // 输出对应自定义异常的报错信息（BaseException类是自定义的，其他只用输出固定message的自定义异常都继承该base并重写msg）
        return Result.error(ex.getMessage());
    }

    /**
     * 处理SQL异常 (插入同一条数据产生的报错)
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex) {
        // Duplicate entry 'zhangsan' for key 'employee.idx_username'
        String msg = ex.getMessage();   // 获取到报错信息
        if(msg.contains("Duplicate entry")) {
            String[] split = msg.split(" ");
            String username = split[2];

            String ret = username + MessageConstant.ALREADY_EXISTS;
            return Result.error(ret);
        } else {
            // 就报未知错误
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }


    }

}
