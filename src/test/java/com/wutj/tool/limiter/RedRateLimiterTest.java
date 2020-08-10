package com.wutj.tool.limiter;

import com.wutj.tool.limiter.test.TestMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Collections;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Main.class)
public class RedRateLimiterTest {

	private static final Logger log = LoggerFactory.getLogger(RedRateLimiterTest.class);

	@Autowired
	StringRedisTemplate template;

	@Autowired
	TestMethod method;

	@Test
	public void testRedRateLimiter() {

		RedRateLimiter limiter = RedRateLimiter.create(4, "junit");
		limiter.acquire();
		log.info("aa");
	}

	@Test
	public void testLua() {
		DefaultRedisScript<Integer> script = new DefaultRedisScript<>();
		script.setScriptText("if redis.call('set',KEYS[1],ARGV[1],'NX','PX',ARGV[2]) then\n" +
				"    return 1\n" +
				"else\n" +
				"    return 0\n" +
				"end");
		script.setResultType(Integer.class);
		Object a = template.execute(script, Collections.singletonList("Lock"), Thread.currentThread().getName(), "3000");
		System.out.println(a);
	}

	@Test
	public void testAnnotation() {
		while (true) {
			method.outPut();
		}
	}
}