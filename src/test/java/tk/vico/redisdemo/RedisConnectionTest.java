package tk.vico.redisdemo;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;

public class RedisConnectionTest {
    public static void main(String[] args) {
        RedisURI redisURI = RedisURI.Builder.redis("127.0.0.1", 6379).build();
        RedisClient redisClient = RedisClient.create(redisURI);
        StatefulRedisConnection connection = redisClient.connect();
        RedisCommands<String, String> commands = connection.sync();

        SetArgs arg = SetArgs.Builder.ex(180)
                .nx();
        commands.set("t", "10", arg);
        commands.watch("t");
        commands.get("t");

        commands.multi();
        commands.decr("t");
        TransactionResult transactionResult = commands.exec();
        if (transactionResult.wasDiscarded()) {
            commands.unwatch();
            System.out.println("cancel!!!");
        }
        connection.close();
    }
}
