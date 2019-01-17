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
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import io.vavr.control.Option;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;


//TODO implement by using pub/sub mode
public class RedisBasedRatelimiterV2 implements RateLimiter {
    private static final String NAME_MUST_NOT_BE_NULL = "Name must not be null";
    private static final String CONFIG_MUST_NOT_BE_NULL = "RateLimiterConfig must not be null";

    private final String name;
    private final AtomicReference<RateLimiterConfig> rateLimiterConfig;
    private final RedisBasedRatelimiterV2.RedisBasedRateLimiterV2Metrics metrics;
    private final RateLimiterEventProcessor eventProcessor;
    private final RedisClient redisClient;

    // 存放pub/sub的数据，内部使用一个listener操作它，填充publish的数据
    private final static Map<String, Integer> concernedMessage = new HashMap<>();
    private final static Map<String, Long> concernedSub = new HashMap<>();

    private RedisPubSubListener<String, Integer> listener;


    public RedisBasedRatelimiterV2(String name, final RateLimiterConfig rateLimiterConfig) {
        this(name, rateLimiterConfig, null);
    }

    public RedisBasedRatelimiterV2(String name, RateLimiterConfig rateLimiterConfig, RedisClient redisClient) {
        this.name = requireNonNull(name, NAME_MUST_NOT_BE_NULL);
        this.redisClient = Option.of(redisClient).getOrElse(this::configureRedisClient);
        this.metrics = this.new RedisBasedRateLimiterV2Metrics();
        this.eventProcessor = new RateLimiterEventProcessor();
        this.rateLimiterConfig = new AtomicReference<>(requireNonNull(initLimit(rateLimiterConfig), CONFIG_MUST_NOT_BE_NULL));
        listener = initRedisPubSubListener();
    }

    private RedisClient configureRedisClient() {
        return SpringContext.getBean(RedisClient.class);
    }

    private RateLimiterConfig initLimit(RateLimiterConfig rateLimiterConfig) {
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisCommands<String, String> commands = connection.sync();
            if (commands.setnx(name, String.valueOf(rateLimiterConfig.getLimitForPeriod()))) {
                commands.expire(name, rateLimiterConfig.getLimitRefreshPeriod().getSeconds());
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

    private RedisPubSubListener initRedisPubSubListener() {
        return new RedisPubSubListener<String, Integer>() {
            @Override
            public void message(String channel, Integer message) {
                concernedMessage.put(channel, message);
            }

            @Override
            public void message(String pattern, String channel, Integer message) {
            }

            @Override
            public void subscribed(String channel, long count) {
                concernedSub.put(channel, count);
            }

            @Override
            public void psubscribed(String pattern, long count) {
            }

            @Override
            public void unsubscribed(String channel, long count) {
            }

            @Override
            public void punsubscribed(String pattern, long count) {
            }
        };
    }

    //每次获取都尝试从redis中获取
    private boolean tryAcquireFromRedis(Duration timeoutDuration) {
        boolean success = true;
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisAsyncCommands<String, String> commands = connection.async();
            RedisFuture<String> redisFuture = commands.get(name);
            String permits = redisFuture.get(timeoutDuration.toNanos(), TimeUnit.NANOSECONDS);
            if (permits != null && Integer.valueOf(permits) > 0) {
                commands.decr(name);
            } else {
                success = false;
            }
        } finally {
            this.redisClient.shutdown();
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
    public RateLimiter.Metrics getMetrics() {
        return this.metrics;
    }

    @Override
    public RateLimiter.EventPublisher getEventPublisher() {
        return eventProcessor;
    }

    private final class RedisBasedRateLimiterV2Metrics implements RateLimiter.Metrics {
        private RedisBasedRateLimiterV2Metrics() {
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
