package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.event.RateLimiterOnFailureEvent;
import io.github.resilience4j.ratelimiter.event.RateLimiterOnSuccessEvent;
import io.github.resilience4j.ratelimiter.internal.RateLimiterEventProcessor;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
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
public class RedisBasedRateLimiter implements RateLimiter {

    private static final String NAME_MUST_NOT_BE_NULL = "Name must not be null";
    private static final String CONFIG_MUST_NOT_BE_NULL = "RateLimiterConfig must not be null";

    private final String name;
    private final AtomicReference<RateLimiterConfig> rateLimiterConfig;
    private final ScheduledExecutorService scheduler;
    private final RedisBasedRateLimiter.RedisBasedRateLimiterMetrics metrics;
    private final RateLimiterEventProcessor eventProcessor;
    private final RedisClient redisClient;


    public RedisBasedRateLimiter(String name, final RateLimiterConfig rateLimiterConfig) {
        this(name, rateLimiterConfig, null, null);
    }

    public RedisBasedRateLimiter(String name, RateLimiterConfig rateLimiterConfig, ScheduledExecutorService scheduler, RedisClient redisClient) {
        this.name = requireNonNull(name, NAME_MUST_NOT_BE_NULL);
        this.redisClient = Option.of(redisClient).getOrElse(this::configureRedisClient);
        this.metrics = this.new RedisBasedRateLimiterMetrics();
        this.eventProcessor = new RateLimiterEventProcessor();
        this.rateLimiterConfig = new AtomicReference<>(requireNonNull(rateLimiterConfig, CONFIG_MUST_NOT_BE_NULL));
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
            commands.set(name, String.valueOf(rateLimiterConfig.get().getLimitForPeriod()));
        }
    }

    private RedisClient configureRedisClient() {
        return SpringContext.getBean(RedisClient.class);
    }

    private boolean tryAcquireFromRedis(Duration timeoutDuration) throws Exception {
        boolean success = true;
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisAsyncCommands<String, String> commands = connection.async();
            RedisFuture<String> redisFuture = commands.get(name);
            String permits = redisFuture.get(timeoutDuration.toMillis(), TimeUnit.MILLISECONDS);
            if (permits != null && Integer.valueOf(permits) > 0) {
                commands.decr(name);
            } else {
                success = false;
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

    private final class RedisBasedRateLimiterMetrics implements Metrics {
        private RedisBasedRateLimiterMetrics() {
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
