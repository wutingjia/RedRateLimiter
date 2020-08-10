package com.wutj.tool.limiter;

import com.google.common.math.LongMath;

import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.SECONDS;

abstract class RedSmoothRateLimiter extends RedRateLimiter {

	/**
	 * 稳定状态下两个请求的时间间隔单位为微秒，例如一秒一个的话即 1000000 / 1
	 */
	double stableIntervalMicros;

	/**
	 * redis中的key
	 */
	String key;

	private RedSmoothRateLimiter(SleepingStopwatch stopwatch) {
		super(stopwatch);
	}

	/**
	 * 设置速率
	 * @param permitsPerSecond 每秒允许的请求数
	 * @param nowMicros 从stopWatch开始启动的微秒数
	 */
	@Override
	final void doSetRate(double permitsPerSecond, long nowMicros) {
		//初始化令牌数
		resync(nowMicros);
		double stableIntervalMicros = SECONDS.toMicros(1L) / permitsPerSecond;
		this.stableIntervalMicros = stableIntervalMicros;
		doSetRate(permitsPerSecond, stableIntervalMicros);
	}

	abstract void doSetRate(double permitsPerSecond, double stableIntervalMicros);

	@Override
	final double doGetRate() {
		return SECONDS.toMicros(1L) / stableIntervalMicros;
	}

	@Override
	final long queryEarliestAvailable(long nowMicros) {
		return RedisUtil.getNextFreeTicketMicros(this.key);
	}

	/**
	 *
	 * @param requiredPermits 请求的令牌数
	 * @param nowMicros 从stopwatch开始启动的毫秒数
	 * @return
	 */
	@Override
	final long reserveEarliestAvailable(int requiredPermits, long nowMicros) {
		resync(nowMicros);
		long returnValue = RedisUtil.getNextFreeTicketMicros(this.key);
		double storedPermits = RedisUtil.getStoredPermits(this.key);
		//实际需要扣除的令牌数
		double storedPermitsToSpend = min(requiredPermits, storedPermits);
		//需要预支的令牌数
		double freshPermits = requiredPermits - storedPermitsToSpend;
		//产生这些预支令牌需要的额外时间
		long waitMicros = storedPermitsToWaitTime(storedPermits, storedPermitsToSpend) + (long) (freshPermits * stableIntervalMicros);
		//将这个时间累加存到redis
		RedisUtil.setNextFreeTicketMicros(this.key, LongMath.saturatedAdd(returnValue, waitMicros));
		//将现有令牌数存到redis
		storedPermits -= storedPermitsToSpend;
		RedisUtil.setStoredPermits(this.key, storedPermits);
		return returnValue;
	}

	abstract long storedPermitsToWaitTime(double storedPermits, double permitsToTake);

	/**
	 * 核心方法，补充令牌数，并且设定下次能获得的时间
	 * @param nowMicros stopwatch开始启动的毫秒数
	 */
	void resync(long nowMicros) {
		//该时间代表下一次请求一定能获得令牌数的绝对时间
		long nextFreeTicketMicros = RedisUtil.getNextFreeTicketMicros(this.key);
		//现在的时间大于上述时间,即可以获取令牌
		if (nowMicros > nextFreeTicketMicros) {
			//在这段时间内匀速应该产生的令牌数
			double newPermits = (nowMicros - nextFreeTicketMicros) / coolDownIntervalMicros();
			//允许储存的最大令牌数
			double maxPermits = RedisUtil.getMaxPermits(this.key);
			//现有令牌数
			double storedPermits = RedisUtil.getStoredPermits(this.key);
			//存入增加以后的现有令牌数
			RedisUtil.setStoredPermits(this.key, min(maxPermits, storedPermits + newPermits));
			//将下一次可以获得令牌的时间调整为现在
			RedisUtil.setNextFreeTicketMicros(this.key, nowMicros);
		}
	}

	abstract double  coolDownIntervalMicros();

	static final class RedSmoothBursty extends RedSmoothRateLimiter {

		final double maxBurstSeconds;

		RedSmoothBursty(SleepingStopwatch stopwatch, double maxBurstSeconds, String key) {
			super(stopwatch);
			this.maxBurstSeconds = maxBurstSeconds;
			this.key = key;
		}

		/**
		 * 设置速率
		 * @param permitsPerSecond 每秒允许的令牌数
		 * @param stableIntervalMicros 平均两个请求之间的时间间隔 单位：毫秒
		 */
		@Override
		void doSetRate(double permitsPerSecond, double stableIntervalMicros) {
			double oldMaxPermits = RedisUtil.getMaxPermits(this.key);
			//已经创建过
			if (permitsPerSecond == oldMaxPermits) {
				return;
			}
			double maxPermits = maxBurstSeconds * permitsPerSecond;
			RedisUtil.setMaxPermits(this.key, maxPermits);
			double storedPermits = RedisUtil.getStoredPermits(this.key);
			if (oldMaxPermits == Double.POSITIVE_INFINITY) {
				storedPermits = maxPermits;
			} else {
				storedPermits = (oldMaxPermits == 0.0) ? 0.0 : storedPermits * maxPermits / oldMaxPermits;
			}
			RedisUtil.setStoredPermits(this.key, storedPermits);
		}

		@Override
		long storedPermitsToWaitTime(double storedPermits, double permitsToTake) {
			return 0L;
		}

		@Override
		double coolDownIntervalMicros() {
			return stableIntervalMicros;
		}
	}
}
