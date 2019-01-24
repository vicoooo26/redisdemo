package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@RunWith(SpringRunner.class)
@SpringBootTest
public class RedisdemoApplicationTests {
    @Autowired
    protected RedisClient redisClient;


    @Test
    public void contextLoads() {
    }


    @Test
    public void testLettuce() throws Exception {
        StatefulRedisConnection connection = redisClient.connect();
        RedisAsyncCommands commands = connection.async();
        commands.watch("default");
        commands.multi();
        commands.decr("default");
        RedisFuture<String> redisFuture = commands.get("default");
        RedisFuture execResult = commands.exec();
        String permits = redisFuture.get(100, TimeUnit.SECONDS);
        System.out.println(permits);
//        RedisCommands commands = connection.sync();
//        commands.set("name", "vico");
        connection.close();
        redisClient.shutdown();
    }

    @Test
    public void testRedisBasedRatelimiter() {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                                                               .limitForPeriod(10)
                                                               .limitRefreshPeriod(Duration.ofSeconds(150))
                                                               //not wait long time , we can handle it using the dead-letter queue
                                                               .timeoutDuration(Duration.ofMillis(100L))
                                                               .build();
        RateLimiter rateLimiter = new RedisBasedRateLimiter("limiter", rateLimiterConfig);
//        Supplier supplier = RateLimiter.decorateSupplier(rateLimiter, () -> {
//            System.out.println("---");
//            return 1;
//        });
//        for (int i = 0; i < 3; i++) {
//            System.out.println(Try.ofSupplier(supplier)
//                    .onFailure((throwable) -> System.out.println("error!"))
//                    .onSuccess((result) -> System.out.println("success and the result is : " + result)));
//        }
    }


    @Test
    public void testMethod() {
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisAsyncCommands<String, String> commands = connection.async();
            RedisFuture<String> redisFuture = commands.get("hello");
            String permits = redisFuture.get(5, TimeUnit.NANOSECONDS);
            if (permits != null && Integer.valueOf(permits) > 0) {
                commands.decr("hello");
            } else {
                System.out.println("out of usage");
            }
        } catch (Exception e) {
            System.out.println("error");
        } finally {
            this.redisClient.shutdown();
        }
    }


    @Test
    public void testLettuceAsync() throws Exception {
        Boolean success = true;
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisAsyncCommands<String, String> commands = connection.async();
            RedisFuture<String> redisFuture = commands.get("limiter");
            String permits = redisFuture.get(Duration.ofMillis(100L).toMillis(), TimeUnit.MILLISECONDS);
            if (permits != null && Integer.valueOf(permits) > 0) {
                commands.decr("limiter");
            } else {
                success = false;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            this.redisClient.shutdown();
            System.out.println(success);
        }
    }
}

