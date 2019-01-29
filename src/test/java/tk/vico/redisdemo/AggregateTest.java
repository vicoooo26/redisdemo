package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class AggregateTest {

    static Map<String, RateLimiter> applicationLimiterContainer = new ConcurrentHashMap<>();
    static List<Long> ratelimiterSum = new ArrayList<>();

    public static void main(String[] args) {
        before();

        long start = System.currentTimeMillis();
        CyclicBarrier barrier = new CyclicBarrier(1000, new Time(start));

        ExecutorService executor = Executors.newFixedThreadPool(1000);
        //起n个线程
        for (int i = 1; i <= 1000; i++) {
            executor.submit(() -> {
                try {
                    long result = ratelimiter();
                    ratelimiterSum.add(result);
                    barrier.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (BrokenBarrierException e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();

    }

    private static long ratelimiter() {
        long start = System.currentTimeMillis();
        //获取哪个ratelimiter
        RateLimiter rateLimiter = applicationLimiterContainer.get("default_local");
        CheckedRunnable runnable = RateLimiter.decorateCheckedRunnable(rateLimiter, () -> System.out.println("executing!!!"));
        Try.run(runnable)
                .onFailure((throwable) -> System.out.println("error: " + throwable.getMessage()));
        long current = System.currentTimeMillis();
        return current - start;
    }


    public static void before() {
        RedisURI redisURI = RedisURI.Builder.redis("127.0.0.1", 6379).build();
        RedisClient redisClient = RedisClient.create(redisURI);
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(2)
                .limitRefreshPeriod(Duration.ofSeconds(180))
                .timeoutDuration(Duration.ofMillis(100L))
                .build();
        //初始化多少个ratelimiter
        for (int i = 1; i <= 1; i++) {
            RateLimiter rateLimiter = new RedisBasedRateLimiterV2("default_local", rateLimiterConfig, redisClient);
            applicationLimiterContainer.put(rateLimiter.getName(), rateLimiter);
        }
    }

    private static class Time implements Runnable {     //用于统计时间
        private long start;

        public Time(long start) {
            this.start = start;
        }

        public void run() {
            System.out.println("耗时:" + (System.currentTimeMillis() - start));
            System.out.println(ratelimiterSum.stream().collect(Collectors.summarizingLong(Long::longValue)));
        }
    }

}
