package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;

import java.time.Duration;
import java.util.function.Supplier;

public class test {
    public static void main(String[] args) {
    }

    public void testListener() {
        RedisURI redisURI = RedisURI.Builder.redis("127.0.0.1", 6379).build();
        RedisClient redisClient = RedisClient.create(redisURI);
        RedisPubSubListener<String, String> listener = new RedisPubSubListener<String, String>() {
            @Override
            public void message(String pattern, String channel) {
                System.out.println("message: " + pattern + ", " + channel);
            }

            @Override
            public void message(String pattern, String channel, String message) {
                System.out.println("message: " + pattern + ", " + channel + ", " + message);
            }

            @Override
            public void psubscribed(String pattern, long count) {
                System.out.println("psub: " + pattern + ", " + count);
            }

            @Override
            public void punsubscribed(String pattern, long count) {
                System.out.println("punsub: " + pattern + ", " + count);
            }

            @Override
            public void subscribed(String channel, long count) {
                System.out.println("sub: " + channel + ", " + count);
            }

            @Override
            public void unsubscribed(String channel, long count) {
                System.out.println("ubsub: " + channel + ", " + count);
            }

        };
        try (StatefulRedisPubSubConnection connection = redisClient.connectPubSub()) {
            connection.addListener(listener);
            RedisPubSubCommands<String, String> commands = connection.sync();
            commands.subscribe("pubsub");
            Thread.sleep(100000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        redisClient.shutdown();
    }
}
