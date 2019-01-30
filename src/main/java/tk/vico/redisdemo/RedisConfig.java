package tk.vico.redisdemo;


import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
@EnableCaching
public class RedisConfig extends CachingConfigurerSupport {

    @Bean
    public RedisClient redisClient(LettuceConnectionFactory factory) {
        RedisURI redisURI = RedisURI.Builder.redis(factory.getHostName(), factory.getPort()).build();
        RedisClient redisClient = RedisClient.create(redisURI);
        return redisClient;
    }
}