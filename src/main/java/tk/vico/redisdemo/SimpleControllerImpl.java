package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.vavr.CheckedRunnable;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimpleControllerImpl implements SimpleController {

    @Autowired
    private RateLimiterFactory rateLimiterFactory;

    @Override
    public void invoke() {
        RateLimiter rateLimiter = rateLimiterFactory.getApplicationLimiter("default");
        CheckedRunnable runnable = RateLimiter.decorateCheckedRunnable(rateLimiter, () -> System.out.println("executing!!!"));
        try {
            Try.run(runnable)
                    .onFailure((throwable) -> new RuntimeException(throwable.getMessage())).get();
//            System.out.println(rateLimiter.getMetrics().getAvailablePermissions());
//            System.out.println(rateLimiter.getMetrics().getNumberOfWaitingThreads());
        } catch (Exception e) {
            System.out.println("error: " + e.getMessage());
        }
    }
}
