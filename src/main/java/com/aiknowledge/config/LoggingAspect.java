package com.aiknowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * AOP日志切面
 *
 * 功能：
 * 1. 为每个请求注入TraceId（链路追踪）
 * 2. 记录Controller方法入参、出参、耗时
 * 3. 异常时记录详细错误信息
 *
 * 这是企业级项目必备的可观测性组件
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    /**
     * 拦截所有Controller方法
     */
    @Around("execution(* com.aiknowledge.controller.*.*(..))")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        // 注入TraceId到MDC（每次请求唯一，方便日志追踪）
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("traceId", traceId);

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
        Object[] args = joinPoint.getArgs();

        long start = System.currentTimeMillis();

        log.info("→ [{}.{}] 请求开始", className, methodName);
        if (log.isDebugEnabled() && args.length > 0) {
            log.debug("  入参: {}", formatArgs(args));
        }

        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;

            if (cost > 5000) {
                log.warn("← [{}.{}] 响应完成，耗时: {}ms （⚠️ 超过5秒）", className, methodName, cost);
            } else {
                log.info("← [{}.{}] 响应完成，耗时: {}ms", className, methodName, cost);
            }

            return result;

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("✕ [{}.{}] 异常，耗时: {}ms，错误: {}",
                    className, methodName, cost, e.getMessage());
            throw e;
        } finally {
            MDC.clear();
        }
    }

    /**
     * 拦截Service层AI调用方法（记录token消耗）
     */
    @Around("execution(* com.aiknowledge.service.impl.AiServiceImpl.*(..))")
    public Object logAiService(ProceedingJoinPoint joinPoint) throws Throwable {
        String method = joinPoint.getSignature().getName();
        long start = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            log.info("AI调用 [{}] 成功，耗时: {}ms", method, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("AI调用 [{}] 失败: {}", method, e.getMessage());
            throw e;
        }
    }

    private String formatArgs(Object[] args) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null) {
                String str = args[i].toString();
                // 截断过长参数
                sb.append(str.length() > 200 ? str.substring(0, 200) + "..." : str);
            } else {
                sb.append("null");
            }
            if (i < args.length - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}
