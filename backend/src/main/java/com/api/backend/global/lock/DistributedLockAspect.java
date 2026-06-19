package com.api.backend.global.lock;

import com.api.backend.global.exception.LockAcquisitionFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint pjp, DistributedLock distributedLock) throws Throwable {
        String key  = resolveKey(pjp, distributedLock.key());
        RLock  lock = redissonClient.getLock(key);

        boolean acquired = lock.tryLock(
            distributedLock.waitTime(),
            distributedLock.leaseTime(),
            distributedLock.timeUnit()
        );

        if (!acquired) {
            throw new LockAcquisitionFailedException(key);
        }

        try {
            return pjp.proceed();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private String resolveKey(ProceedingJoinPoint pjp, String keyExpression) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method             = signature.getMethod();
        String[] paramNames       = signature.getParameterNames();
        Object[] args             = pjp.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
