package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterFactory {

    @Value("${round}")
    private int round;

    Map<String, RateLimiter> applicationLimiterContainer = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public Map<String, RateLimiter> loadAllApplicationLimiters() {
        if (applicationLimiterContainer.isEmpty()) {
            synchronized (RateLimiterFactory.class) {
                if (applicationLimiterContainer.isEmpty()) {
                    RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                            .limitForPeriod(10)
                            .limitRefreshPeriod(Duration.ofSeconds(180))
                            .timeoutDuration(Duration.ofMillis(100L))
                            .build();
//                    initAllRatelimiter(rateLimiterConfig);
                    applicationLimiterContainer.put("default", new RedisBasedRateLimiterV3("default", rateLimiterConfig));
                }
            }
        }
        return applicationLimiterContainer;
    }

    public RateLimiter getApplicationLimiter(String name) {
        return applicationLimiterContainer.get(name);
    }

    public void initAllRatelimiter(RateLimiterConfig rateLimiterConfig) {
        for (int i = 1; i <= round; i++) {
            RateLimiter rateLimiter = new RedisBasedRateLimiterV2("default_" + i, rateLimiterConfig);
            applicationLimiterContainer.put("default_" + i, rateLimiter);
        }
    }
}
