package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.vavr.CheckedFunction0;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimpleControllerImpl implements SimpleController {

    @Autowired
    private RateLimiterFactory rateLimiterFactory;

    @Override
    public String invoke() {
        RateLimiter rateLimiter = rateLimiterFactory.getApplicationLimiter("default");
        CheckedFunction0<String> function0 = RateLimiter.decorateCheckedSupplier(rateLimiter, () -> "executing!!!");
        String result;
        try {
            result = Try.of(function0)
                    .onFailure((throwable) -> new RuntimeException(throwable.getMessage())).get();
//            System.out.println(rateLimiter.getMetrics().getAvailablePermissions());
//            System.out.println(rateLimiter.getMetrics().getNumberOfWaitingThreads());
        } catch (Exception e) {
            result = e.getMessage();
        }
        return result;
    }
}
