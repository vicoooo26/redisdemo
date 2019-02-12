package tk.vico.redisdemo;


import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@Configuration
@EnableConfigurationProperties
public class RedisConfig extends CachingConfigurerSupport {
    @Autowired
    RedissonConfig redissonConfig;

    @Bean
    public RedisClient redisClient(LettuceConnectionFactory factory) {
        RedisURI redisURI = RedisURI.Builder.redis(factory.getHostName(), factory.getPort()).build();
        RedisClient redisClient = RedisClient.create(redisURI);
        return redisClient;
    }

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setCodec(new org.redisson.client.codec.StringCodec());
        config.useSingleServer().setAddress(redissonConfig.toString()).setPassword(null);
        return Redisson.create(config);
    }
}