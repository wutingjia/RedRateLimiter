package com.wutj.tool.limiter.test;

import com.wutj.tool.limiter.Limiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TestMethod {
	private static final Logger log = LoggerFactory.getLogger(TestMethod.class);

	@Limiter(permits = 4, key = "junit")
	public void outPut() {
		log.info("aa");
	}
}
