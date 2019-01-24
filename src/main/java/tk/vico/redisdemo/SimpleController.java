package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.vavr.CheckedFunction0;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimpleController {

    @Autowired
    private RateLimiterFactory rateLimiterFactory;

    @RequestMapping(value = "/invoke", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public String invoke() {
        RateLimiter rateLimiter = rateLimiterFactory.getApplicationLimiter("default");
        CheckedFunction0<String> function0 = RateLimiter.decorateCheckedSupplier(rateLimiter, () -> "executing!!!");
        String result;
        try {
            result = Try.of(function0)
                        .onFailure((throwable) -> new RuntimeException(throwable.getMessage())).get();
            System.out.println(rateLimiter.getMetrics().getAvailablePermissions());
            System.out.println(rateLimiter.getMetrics().getNumberOfWaitingThreads());
        } catch (Exception e) {
            result = e.getMessage();
        }
        return result;
    }
}
