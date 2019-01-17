package tk.vico.redisdemo;

import ch.qos.logback.core.rolling.helper.IntegerTokenConverter;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimpleController {

    @Autowired
    private RedisClient redisClient;

    @RequestMapping(value = "/invoke", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public String invoke() {
        return "foo";
    }
}
