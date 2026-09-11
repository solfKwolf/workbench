package com.workbench.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 拦截所有模块 controller 包下的接口，统一记录入参/出参/耗时。
 * 密码脱敏靠 DTO 上的 @ToString(exclude = "password")——本切面打印的是 toString 结果。
 */
@Slf4j
@Aspect
@Component
public class RequestLogAspect {

    @Pointcut("execution(* com.workbench..controller..*(..))")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        long start = System.currentTimeMillis();
        log.info("--> {} 入参: {}", method, Arrays.toString(pjp.getArgs()));
        try {
            Object result = pjp.proceed();
            log.info("<-- {} 出参: {} 耗时: {}ms",
                    method, result, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            log.error("<!! {} 异常: {} 耗时: {}ms",
                    method, e.getMessage(), System.currentTimeMillis() - start);
            throw e;   // 原样抛出，交给全局异常处理器
        }
    }
}
