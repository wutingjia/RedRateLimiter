package com.wutj.tool.limiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.StringUtils;

import java.util.Collections;

class RedisUtil {

	private RedisUtil(){}

	private static StringRedisTemplate template;

	static double getMaxPermits(String key) {

		String maxPermits = (String)template.opsForHash().get(key, "maxPermits");
		if ("null".equals(maxPermits) || StringUtils.isEmpty(maxPermits)) {
			return 0.0;
		}else {
			return Double.parseDouble(maxPermits);
		}
	}

	static void setMaxPermits(String key, double maxPermits) {
		template.opsForHash().put(key, "maxPermits", String.valueOf(maxPermits));
	}

	static double getStoredPermits(String key) {
		String storedPermits = (String)template.opsForHash().get(key, "storedPermits");
		if ("null".equals(storedPermits) || StringUtils.isEmpty(storedPermits)) {
			return 0.0;
		}else {
			return Double.parseDouble(storedPermits);
		}
	}

	static void setStoredPermits(String key, double storedPermits) {
		template.opsForHash().put(key, "storedPermits", String.valueOf(storedPermits));
	}

	static long getNextFreeTicketMicros(String key) {
		String nextFreeTicketMicros = (String)template.opsForHash().get(key, "nextFreeTicketMicros");
		if ("null".equals(nextFreeTicketMicros) || StringUtils.isEmpty(nextFreeTicketMicros)) {
			return 0L;
		}else {
			return Long.parseLong(nextFreeTicketMicros);
		}
	}

	static void setNextFreeTicketMicros(String key, long nextFreeTicketMicros) {
		template.opsForHash().put(key, "nextFreeTicketMicros", String.valueOf(nextFreeTicketMicros));
	}

	static void lock(String key) {
		synchronized (RedisUtil.class) {
			if (template == null) {
				template = (StringRedisTemplate)SpringBeanUtil.getBean("stringRedisTemplate");
			}
		}

		DefaultRedisScript<String> script = new DefaultRedisScript<>();
		script.setResultType(String.class);
		script.setScriptText("if redis.call('set',KEYS[1],ARGV[1],'NX','PX',ARGV[2]) then\n" +
				"    return '1'\n" +
				"else\n" +
				"    return '0'\n" +
				"end");

		while (!"1".equals(template.execute(script, Collections.singletonList(key + "Lock"), Thread.currentThread().getName(), "3000"))) {
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	static void unlock(String key) {
		DefaultRedisScript<String> script = new DefaultRedisScript<>();
		script.setResultType(String.class);
		script.setScriptText("if redis.call('get',KEYS[1]) == ARGV[1] then\n" +
				"    return tostring(redis.call('del',KEYS[1]))\n" +
				"else\n" +
				"    return '0'\n" +
				"end");
		template.execute(script,Collections.singletonList(key + "Lock"), Thread.currentThread().getName());
	}
}
