package com.wutj.tool.limiter;

import java.lang.annotation.*;

/**
 * 限流
 */
@Target(value = ElementType.METHOD)
@Retention(value = RetentionPolicy.RUNTIME)
@Documented
public @interface Limiter {

	/**
	 * 每秒允许的令牌数
	 */
	double permits();

	/**
	 * 使用的限流器id
	 */
	String key();
}
