package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.vavr.control.Try;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.function.Supplier;

@RestController
public class SimpleController {
    private static String information;

    @Autowired
    private RateLimiterFactory rateLimiterFactory;

    @RequestMapping(value = "/invoke", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public String invoke() {
        RateLimiter rateLimiter = rateLimiterFactory.getApplicationLimiter("default");
        Supplier<String> supplier = RateLimiter.decorateSupplier(rateLimiter, () -> "executing!!!");
        return Try.ofSupplier(supplier)
                .onFailure((throwable) -> System.out.println("error!"))
                .onSuccess((result) -> System.out.println("success and the result is : " + result)).get();
    }
}

