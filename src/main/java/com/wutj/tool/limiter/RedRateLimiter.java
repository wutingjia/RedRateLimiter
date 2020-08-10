package com.wutj.tool.limiter;

import com.google.common.util.concurrent.Uninterruptibles;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author wutingjia
 */
public abstract class RedRateLimiter {

	/**
	 * 限流器的id
	 */
	private static String key;

	/**
	 * 静态方法创建一个RedRateLimiter
	 * @param permitsPerSecond 每秒允许的令牌数
	 * @param key redis中的key名，同样的key会使用同一个限流器
	 * @return RedRateLimiter
	 */
	public static RedRateLimiter create(double permitsPerSecond, String key) {
		RedRateLimiter.key = key;
		return create(permitsPerSecond, SleepingStopwatch.createFromSystemTimer(), key);
	}

	static RedRateLimiter create(double permitsPerSecond, SleepingStopwatch stopwatch, String key) {
		RedRateLimiter redRateLimiter = new RedSmoothRateLimiter.RedSmoothBursty(stopwatch, 1.0, key);
		redRateLimiter.setRate(permitsPerSecond);
		return redRateLimiter;
	}

	private final SleepingStopwatch stopwatch;

	RedRateLimiter(SleepingStopwatch stopwatch) {
		this.stopwatch = stopwatch;
	}


	public final void setRate(double permitsPerSecond) {
		if (permitsPerSecond <= 0 || Double.isNaN(permitsPerSecond)) {
			throw new IllegalArgumentException("速率不能为非正数");
		}

		RedisUtil.lock(key);
		doSetRate(permitsPerSecond, stopwatch.readMicros());
		RedisUtil.unlock(key);
	}

	abstract void doSetRate(double permitsPerSecond, long nowMicros);

	public final double getRate() {
		RedisUtil.lock(key);
		double res = doGetRate();
		RedisUtil.unlock(key);
		return res;
	}

	abstract double doGetRate();

	public double acquire() {
		return acquire(1);
	}

	public double acquire(int permits) {
		long microsToWait = reserve(permits);
		stopwatch.sleepMicrosUninterruptibly(microsToWait);
		return 1.0 * microsToWait / SECONDS.toMicros(1L);
	}

	/**
	 * 预留permit供将来使用
	 * @param permits 需要预留的个数
	 * @return 下一个资源获取所需要等待的时间 单位：微秒
	 */
	final long reserve(int permits) {
		if (permits <= 0) {
			throw new IllegalArgumentException("速率不能为非正数");
		}
		RedisUtil.lock(key);
		long res = reserveAndGetWaitLength(permits, stopwatch.readMicros());
		RedisUtil.unlock(key);
		return res;
	}

	public boolean tryAcquire(Duration timeout) {
		return tryAcquire(1, timeout.toNanos(), TimeUnit.NANOSECONDS);
	}

	public boolean tryAcquire(long timeout, TimeUnit unit) {
		return tryAcquire(1, timeout, unit);
	}

	public boolean tryAcquire(int permits) {
		return tryAcquire(permits, 0, MICROSECONDS);
	}

	public boolean tryAcquire() {
		return tryAcquire(1, 0, MICROSECONDS);
	}

	public boolean tryAcquire(int permits, Duration timeout) {
		return tryAcquire(permits, timeout.toNanos(), TimeUnit.NANOSECONDS);
	}


	public boolean tryAcquire(int permits, long timeout, TimeUnit unit) {
		long timeoutMicros = Math.max(unit.toMicros(timeout), 0);
		if (permits <= 0) {
			throw new IllegalArgumentException("速率不能为非正数");
		}
		long microsToWait;
		RedisUtil.lock(key);
			long nowMicros = stopwatch.readMicros();
			if (!canAcquire(nowMicros, timeoutMicros)) {
				return false;
			} else {
				microsToWait = reserveAndGetWaitLength(permits, nowMicros);
			}
		RedisUtil.unlock(key);

		stopwatch.sleepMicrosUninterruptibly(microsToWait);
		return true;
	}

	private boolean canAcquire(long nowMicros, long timeoutMicros) {
		return queryEarliestAvailable(nowMicros) - timeoutMicros <= nowMicros;
	}

	abstract long queryEarliestAvailable(long nowMicros);

	final long reserveAndGetWaitLength(int permits, long nowMicros) {
		//如果现在时间大于redis中预存的下次时间，返回当前时间.否则返回预存时间
		long momentAvailable = reserveEarliestAvailable(permits, nowMicros);
		return Math.max(momentAvailable - nowMicros, 0);
	}

	abstract long reserveEarliestAvailable(int permits, long nowMicros);

	@Override
	public String toString() {
		return String.format("DistributedRateLimiter[stableRate=%3.1fqps]", getRate());
	}

	abstract static class SleepingStopwatch {

		protected SleepingStopwatch() {}

		protected abstract long readMicros();

		protected abstract void sleepMicrosUninterruptibly(long micros);

		public static SleepingStopwatch createFromSystemTimer() {
			return new SleepingStopwatch() {

				@Override
				protected long readMicros() {
					return System.currentTimeMillis() * 1000;
				}

				@Override
				protected void sleepMicrosUninterruptibly(long micros) {
					if (micros > 0) {
						Uninterruptibles.sleepUninterruptibly(micros, MICROSECONDS);
					}
				}
			};
		}
	}
}
