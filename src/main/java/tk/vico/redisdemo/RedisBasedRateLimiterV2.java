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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;


public class RedisBasedRateLimiterV2 implements RateLimiter {

    private static final String NAME_MUST_NOT_BE_NULL = "Name must not be null";
    private static final String CONFIG_MUST_NOT_BE_NULL = "RateLimiterConfig must not be null";

    private final String name;
    private final AtomicReference<RateLimiterConfig> rateLimiterConfig;
    private final RedisBasedRateLimiterV2.RedisBasedRateLimiterV3Metrics metrics;
    private final RateLimiterEventProcessor eventProcessor;
    private final RedisClient redisClient;


    public RedisBasedRateLimiterV2(String name, final RateLimiterConfig rateLimiterConfig) {
        this(name, rateLimiterConfig, null);
    }

    public RedisBasedRateLimiterV2(String name, RateLimiterConfig rateLimiterConfig, RedisClient redisClient) {
        this.name = requireNonNull(name, NAME_MUST_NOT_BE_NULL);
        this.redisClient = Option.of(redisClient).getOrElse(this::configureRedisClient);
        this.metrics = this.new RedisBasedRateLimiterV3Metrics();
        this.eventProcessor = new RateLimiterEventProcessor();
        this.rateLimiterConfig = new AtomicReference<>(requireNonNull(initLimit(rateLimiterConfig), CONFIG_MUST_NOT_BE_NULL));
    }

    private RateLimiterConfig initLimit(RateLimiterConfig rateLimiterConfig) {
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            SetArgs args = SetArgs.Builder.ex(rateLimiterConfig.getLimitRefreshPeriod().getSeconds())
                    .nx();
            if ("OK".equals(commands.set(name, String.valueOf(rateLimiterConfig.getLimitForPeriod()), args))) {

            } else {
                String permitsInRedis = commands.get(name);
                int permits = Integer.valueOf(permitsInRedis);
                if (permits <= 0) {
                    permits = 1;
                }
                rateLimiterConfig = RateLimiterConfig.from(rateLimiterConfig)
                        .limitForPeriod(permits)
                        .build();
            }
        }
        return rateLimiterConfig;
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
            RedisFuture<String> redisFuture = commands.get(name);
            String permits = redisFuture.get(timeoutDuration.toMillis(), TimeUnit.MILLISECONDS);
            if (permits == null) {
                commands.set(name, String.valueOf(rateLimiterConfig.get().getLimitForPeriod() - 1), args);
            } else if (Integer.parseInt(permits) > 0) {
                commands.watch(name);
                commands.multi();
                commands.decr(name);
                RedisFuture transactionFuture = commands.exec();
                if (transactionFuture.isCancelled()) {
                    //TODO need to clarify
                    if (commands.decr(name).get() < 0) {
                        success = false;
                    }
                }
            } else
                success = false;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
        return success;
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
            try {
                return Integer.parseInt(redisClient.connect().sync().get(name));
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        @Override
        public int getNumberOfWaitingThreads() {
            try {
                return redisClient.getResources().computationThreadPoolSize();
            } catch (NumberFormatException e) {
                return -1;
            }
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
