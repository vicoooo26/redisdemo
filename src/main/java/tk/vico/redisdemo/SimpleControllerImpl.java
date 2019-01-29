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
        CheckedFunction0<String> function = RateLimiter.decorateCheckedSupplier(rateLimiter, () -> ("executing!!!"));
        String result = Try.of(function)
                .recover((throwable) -> "error: " + throwable.getMessage()).get();
        return result;
    }

    @Override
    public String calculateAverageTime() {
        return null;
    }
}
