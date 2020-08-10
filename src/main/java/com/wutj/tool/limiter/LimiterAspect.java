package com.wutj.tool.limiter;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Aspect
public class LimiterAspect {

	private volatile Map<String, RedRateLimiter> limiterCache = new ConcurrentHashMap<>();

	@Before("within(cn.bos..*) && @annotation(com.wutj.tool.limiter.Limiter)")
	public void doLimit(JoinPoint jp) throws NoSuchMethodException {
		MethodSignature signature = (MethodSignature)jp.getSignature();
		Method method = jp.getTarget().getClass().getDeclaredMethod(signature.getName(), signature.getMethod().getParameterTypes());
		Limiter limiter = method.getAnnotation(Limiter.class);
		double permits = limiter.permits();
		String key = limiter.key();

		RedRateLimiter redRateLimiter = limiterCache.get(key);
		if (redRateLimiter == null) {
			redRateLimiter = RedRateLimiter.create(permits, key);
			limiterCache.put(key, redRateLimiter);
		}
		redRateLimiter.acquire();
	}

//	@Before("within(cn.bos.*)")
//	public void doLimit1(JoinPoint jp) {
//		System.out.println("a");
//	}
}
