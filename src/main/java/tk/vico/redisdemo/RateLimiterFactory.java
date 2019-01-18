package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterFactory {
    Map<String, RateLimiter> applicationLimiterContainer = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public Map<String, RateLimiter> loadAllApplicationLimiters() {
        if (applicationLimiterContainer.isEmpty()) {
            synchronized (RateLimiterFactory.class) {
                if (applicationLimiterContainer.isEmpty()) {
                    RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                            .limitForPeriod(10)
                            .limitRefreshPeriod(Duration.ofSeconds(60))
                            .timeoutDuration(Duration.ofMillis(100L))
                            .build();
                    RateLimiter rateLimiter = new RedisBasedRateLimiterV2("default", rateLimiterConfig);
                    applicationLimiterContainer.put("default", rateLimiter);
                }
            }
        }
        return applicationLimiterContainer;
    }

    public RateLimiter getApplicationLimiter(String name) {
        return applicationLimiterContainer.get(name);
    }
}
