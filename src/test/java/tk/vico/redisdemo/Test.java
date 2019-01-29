package tk.vico.redisdemo;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Consumer;
import java.util.function.Function;


public class Test {
    public static void main(String[] args) {
//        String[] array = {"333","22","1"};
//        Arrays.sort(array, Comparator.comparingInt(str -> str.length()));
//        //java编译器它能感知离上下文最近的,上面的Comparator可以在当前上下文中直接获取到
//        //下面的Comparator是经过reversed()转换后得到的，无法直接从当前上下文获取,需要显式指定为String
//        Arrays.sort(array, Comparator.comparingInt((String str) -> str.length()).reversed());
//
//        Arrays.stream(array).
//                forEach(out::println);
//
//        Set<String> names = Set.of("abc", "def", "ghi");
//        Integer someVariable = 100;
//        names.forEach(name ->
//        {
//            someVariable = 200; // This line does not compile.
// Gives the compilation error — Local variable someVariable defined in an enclosing scope must be final or effectively final
//        });
//        String outerValue = "Outer class value";
//        Foo fooIC = new Foo() {
//            String outerValue = "Inner class value";
//
//            @Override
//            public String method(String string) {
//                return outerValue + string;
//            }
//        };
//        String resultIC = fooIC.method("!!!");
//        out.println(resultIC);
//
//        Foo fooLambda = parameter -> {
////            String outerValue = "Lambda value";
//            return outerValue + parameter;
//        };
//        String resultLambda = fooLambda.method("");
//
//        out.println("Results: resultIC = " + resultIC +
//                ", resultLambda = " + resultLambda);
//        AdderImpl adder = new AdderImpl();
//        System.out.println(adder.add(a -> a + " from lambda"));
//        adder.add((Integer a) -> {
//            a = a + a;
//            System.out.println(a);
//        });
//
//        int[] total = new int[1];
//        Runnable r = () -> total[0]++;
//
//        for (int i = 0; i < 5; i++) {
//            r.run();
//        }
//
//        for (int i = 0; i < total.length; i++) {
//            System.out.println(total[i]);
//        }

//        final CountDownLatch latch = new CountDownLatch(2);
//
//        new Thread(() -> {
//            try {
//                System.out.println("子线程" + Thread.currentThread().getName() + "正在执行");
//                Thread.sleep(3000);
//                System.out.println("子线程" + Thread.currentThread().getName() + "执行完毕");
//                latch.countDown();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }).start();
//
//        new Thread(() -> {
//            try {
//                System.out.println("子线程" + Thread.currentThread().getName() + "正在执行");
//                Thread.sleep(3000);
//                System.out.println("子线程" + Thread.currentThread().getName() + "执行完毕");
//                latch.countDown();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }).start();
//
//        try {
//            System.out.println("等待2个子线程执行完毕...");
//            latch.await();
//            System.out.println("2个子线程已经执行完毕");
//            System.out.println("继续执行主线程");
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        int N = 4;
        CyclicBarrier barrier = new CyclicBarrier(N, () -> System.out.println("当前线程" + Thread.currentThread().getName()));

        for (int i = 0; i < N; i++)
            new Writer(barrier).start();
    }

    static class Writer extends Thread {
        private CyclicBarrier cyclicBarrier;

        public Writer(CyclicBarrier cyclicBarrier) {
            this.cyclicBarrier = cyclicBarrier;
        }

        @Override
        public void run() {
            System.out.println("线程" + Thread.currentThread().getName() + "正在写入数据...");
            try {
                Thread.sleep(5000);      //以睡眠来模拟写入数据操作
                System.out.println("线程" + Thread.currentThread().getName() + "写入数据完毕，等待其他线程写入完毕");
                cyclicBarrier.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (BrokenBarrierException e) {
                e.printStackTrace();
            }
            System.out.println("所有线程写入完毕，继续处理其他任务...");
        }
    }

    public void testListener() {
        RedisURI redisURI = RedisURI.Builder.redis("127.0.0.1", 6379).build();
        RedisClient redisClient = RedisClient.create(redisURI);
        RedisPubSubListener<String, String> listener = new RedisPubSubListener<String, String>() {
            @Override
            public void message(String pattern, String channel) {
                System.out.println("message: " + pattern + ", " + channel);
            }

            @Override
            public void message(String pattern, String channel, String message) {
                System.out.println("message: " + pattern + ", " + channel + ", " + message);
            }

            @Override
            public void psubscribed(String pattern, long count) {
                System.out.println("psub: " + pattern + ", " + count);
            }

            @Override
            public void punsubscribed(String pattern, long count) {
                System.out.println("punsub: " + pattern + ", " + count);
            }

            @Override
            public void subscribed(String channel, long count) {
                System.out.println("sub: " + channel + ", " + count);
            }

            @Override
            public void unsubscribed(String channel, long count) {
                System.out.println("ubsub: " + channel + ", " + count);
            }

        };
        try (StatefulRedisPubSubConnection<String, String> connection = redisClient.connectPubSub()) {
            connection.addListener(listener);
            RedisPubSubCommands<String, String> commands = connection.sync();
            commands.subscribe("pubsub");
            Thread.sleep(100000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        redisClient.shutdown();
    }

    static void consumeA(Integer a) {
        a = a + a;
        System.out.println(a);
    }


}

interface Adder {
    String add(Function<String, String> f);

    void add(Consumer<Integer> f);
}

class AdderImpl implements Adder {

    @Override
    public String add(Function<String, String> f) {
        return f.apply("Something ");
    }

    @Override
    public void add(Consumer<Integer> f) {
        f.accept(2);
    }
}

