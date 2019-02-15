package tk.vico.redisdemo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

//@Component
@Getter
@Setter
//@ConfigurationProperties(prefix = "redisson")
public class RedissonConfig {
    String port;
    String host;

    @Override
    public String toString() {
        return "redis://" + host + ":" + port;
    }
}
