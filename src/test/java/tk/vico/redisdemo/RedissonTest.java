package tk.vico.redisdemo;

import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.concurrent.TimeUnit;

public class RedissonTest {

    public static void main(String[] args) {
        Config config = new Config();
        config.setCodec(new org.redisson.client.codec.StringCodec());
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword(null);
        RedissonClient client = Redisson.create(config);
//        RAtomicLong atomicLong = client.getAtomicLong("r");
//        atomicLong.set(2);
//        System.out.println(atomicLong.incrementAndGet());
//        RBucket rBucket = client.getBucket("redisson");
//        System.out.println(rBucket.trySet(2, 60, TimeUnit.SECONDS));
//        RSemaphore semaphore = client.getSemaphore("semaphore");
//        semaphore.trySetPermits(3);
//        try {
//            semaphore.acquire();
//        } catch (InterruptedException e) {
//
//        }
        RSemaphore semaphore = client.getSemaphore("semaphores");
//        RBucket rBucket = client.getBucket("semaphores");
//        System.out.println(rBucket.trySet(2, 60, TimeUnit.SECONDS));
        if (semaphore.tryAcquire()) {
            System.out.println("!!!!!!!!!!");
        } else {
            System.out.println("eeeeeeeeee");
        }
    }
}
