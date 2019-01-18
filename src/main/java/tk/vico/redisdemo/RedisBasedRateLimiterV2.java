package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.event.RateLimiterOnFailureEvent;
import io.github.resilience4j.ratelimiter.event.RateLimiterOnSuccessEvent;
import io.github.resilience4j.ratelimiter.internal.RateLimiterEventProcessor;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.vavr.control.Option;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;


//TODO shutdown redisClient properly
public class RedisBasedRateLimiterV2 implements RateLimiter {

    private static final String NAME_MUST_NOT_BE_NULL = "Name must not be null";
    private static final String CONFIG_MUST_NOT_BE_NULL = "RateLimiterConfig must not be null";

    private final String name;
    private final AtomicReference<RateLimiterConfig> rateLimiterConfig;
    private final RedisBasedRateLimiterV2.RedisBasedRateLimiterV3Metrics metrics;
    private final RateLimiterEventProcessor eventProcessor;
    private final RedisClient redisClient;
    private final ScheduledExecutorService scheduler;


    public RedisBasedRateLimiterV2(String name, final RateLimiterConfig rateLimiterConfig) {
        this(name, rateLimiterConfig, null, null);
    }

    public RedisBasedRateLimiterV2(String name, RateLimiterConfig rateLimiterConfig, ScheduledExecutorService scheduler, RedisClient redisClient) {
        this.name = requireNonNull(name, NAME_MUST_NOT_BE_NULL);
        this.redisClient = Option.of(redisClient).getOrElse(this::configureRedisClient);
        this.metrics = this.new RedisBasedRateLimiterV3Metrics();
        this.eventProcessor = new RateLimiterEventProcessor();
        this.rateLimiterConfig = new AtomicReference<>(requireNonNull(initLimit(rateLimiterConfig), CONFIG_MUST_NOT_BE_NULL));
        this.scheduler = Option.of(scheduler).getOrElse(this::configureScheduler);
        scheduleLimitRefresh();
    }

    private ScheduledExecutorService configureScheduler() {
        ThreadFactory threadFactory = target -> {
            Thread thread = new Thread(target, "SchedulerForSemaphoreBasedRateLimiterImpl-" + name);
            thread.setDaemon(true);
            return thread;
        };
        return newSingleThreadScheduledExecutor(threadFactory);
    }


    private void scheduleLimitRefresh() {
        scheduler.scheduleAtFixedRate(
                this::refreshLimit,
                this.rateLimiterConfig.get().getLimitRefreshPeriodInNanos(),
                this.rateLimiterConfig.get().getLimitRefreshPeriodInNanos(),
                TimeUnit.NANOSECONDS
        );
    }

    void refreshLimit() {
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            SetArgs args = SetArgs.Builder.ex(rateLimiterConfig.get().getLimitRefreshPeriod().getSeconds())
                    .nx();
            commands.set(name, String.valueOf(rateLimiterConfig.get().getLimitForPeriod()), args);
        }
    }

    private RateLimiterConfig initLimit(RateLimiterConfig rateLimiterConfig) {
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            SetArgs args = SetArgs.Builder.ex(rateLimiterConfig.getLimitRefreshPeriod().getSeconds())
                    .nx();
            if ("OK".equals(commands.set(name, String.valueOf(rateLimiterConfig.getLimitForPeriod()), args))) {

            } else {
                String permitsInRedis = commands.get(name);
                // 即当一个JVM使用掉Redis中最后一个permit且permits未刷新
                // 在同一个周期内另外一个JVM中欲构建Ratelimiter，其从Redis中获取的permits为0
                // 此时无法构建RateLimiterConfig以及RateLimiter
                // 故为其添加一个，但在redis中的permit仍然为0
                // 保证RateLimiter成功创建，但仍然调用被限
                int permits = Integer.valueOf(permitsInRedis);
                if (permits == 0) {
                    permits = 1;
                }
                rateLimiterConfig = RateLimiterConfig.from(rateLimiterConfig)
                        .limitForPeriod(permits)
                        .build();
            }
        } finally {
            return rateLimiterConfig;
        }
    }


    private RedisClient configureRedisClient() {
        return SpringContext.getBean(RedisClient.class);
    }

    private boolean tryAcquireFromRedis(Duration timeoutDuration) throws Exception {
        boolean success = true;
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisAsyncCommands<String, String> commands = connection.async();
            SetArgs args = SetArgs.Builder.ex(rateLimiterConfig.get().getLimitRefreshPeriod().getSeconds())
                    .nx();
            if ("OK".equals(commands.set(name, String.valueOf(rateLimiterConfig.get().getLimitForPeriod()), args))) {
                success = true;
            } else {
                RedisFuture<String> redisFuture = commands.get(name);
                String permits = redisFuture.get(timeoutDuration.toMillis(), TimeUnit.MILLISECONDS);
                if (permits != null && Integer.valueOf(permits) > 0) {
                    commands.decr(name);
                } else {
                    success = false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            success = false;
            throw e;
        } finally {
            return success;
        }
    }

    @Override
    public void changeTimeoutDuration(Duration timeoutDuration) {
        RateLimiterConfig newConfig = RateLimiterConfig.from(rateLimiterConfig.get())
                .timeoutDuration(timeoutDuration)
                .build();
        rateLimiterConfig.set(newConfig);
    }

    @Override
    public void changeLimitForPeriod(int limitForPeriod) {
        RateLimiterConfig newConfig = RateLimiterConfig.from(rateLimiterConfig.get())
                .limitForPeriod(limitForPeriod)
                .build();
        rateLimiterConfig.set(newConfig);
    }

    @Override
    public boolean getPermission(Duration timeoutDuration) {
        try {
            boolean success = tryAcquireFromRedis(timeoutDuration);
            publishRateLimiterEvent(success);
            return success;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            publishRateLimiterEvent(false);
            return false;
        }
    }

    @Override
    public long reservePermission(Duration timeoutDuration) {
        return -1;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public RateLimiterConfig getRateLimiterConfig() {
        return this.rateLimiterConfig.get();
    }

    @Override
    public Metrics getMetrics() {
        return this.metrics;
    }

    @Override
    public EventPublisher getEventPublisher() {
        return eventProcessor;
    }

    private final class RedisBasedRateLimiterV3Metrics implements Metrics {
        private RedisBasedRateLimiterV3Metrics() {
        }

        @Override
        public int getAvailablePermissions() {
            //TODO need to implement
            return 0;
        }

        @Override
        public int getNumberOfWaitingThreads() {
            //TODO need to implement
            return 0;
        }

    }

    private void publishRateLimiterEvent(boolean permissionAcquired) {
        if (!eventProcessor.hasConsumers()) {
            return;
        }
        if (permissionAcquired) {
            eventProcessor.consumeEvent(new RateLimiterOnSuccessEvent(name));
            return;
        }
        eventProcessor.consumeEvent(new RateLimiterOnFailureEvent(name));
    }


    @Override
    public String toString() {
        return "RedisBasedRateLimiter{" +
                "name='" + name + '\'' +
                ", rateLimiterConfig=" + rateLimiterConfig +
                '}';
    }
}
