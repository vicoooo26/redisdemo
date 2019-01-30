package tk.vico.redisdemo;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.MockitoTestExecutionListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@TestExecutionListeners(listeners = MockitoTestExecutionListener.class)
public class RedisdemoApplicationTests {
    @Autowired
    protected RedisClient redisClient;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static MockMvc mockMvc;
    static List<Long> ratelimiterSum = new ArrayList<>();

    @Mock
    private List mockList;

    @MockBean
    private MyDictionary myDictionary;

    @Mock
    Map<String, String> wordMap;

    @InjectMocks
    MyDictionary dic = new MyDictionary();

    @Test
    public void whenUseInjectMocksAnnotation_thenCorrect() {
        Mockito.when(wordMap.get("aWord")).thenReturn("aMeaning");

        assertEquals("aMeaning", dic.getMeaning("aWord"));
    }


    @Test
    public void contextLoads() {
    }

    @Test
    public void createMock() {
        assertTrue(mockList instanceof List);
        // mock 方法不仅可以 Mock 接口类, 还可以 Mock 具体的类型.
        ArrayList mockedArrayList = mock(ArrayList.class);
        assertTrue(mockedArrayList instanceof List);
        assertTrue(mockedArrayList instanceof ArrayList);
    }

    @Test
    public void testInjectMock() {
    }

    @Test
    public void testSpy() {
        List list = new LinkedList();
        List spy = spy(list);

        // 对 spy.size() 进行定制.
        when(spy.size()).thenReturn(100);

        spy.add("one");
        spy.add("two");

        // 因为我们没有对 get(0), get(1) 方法进行定制,
        // 因此这些调用其实是调用的真实对象的方法.
        assertEquals(spy.get(0), "one");
        assertEquals(spy.get(1), "two");

        assertEquals(spy.size(), 100);


    }

    @Test
    public void testVerify() {
        List mockedList = mock(List.class);
        mockedList.add("one");
        mockedList.add("two");
        mockedList.add("three times");
        mockedList.add("three times");
        mockedList.add("three times");
        when(mockedList.size()).thenReturn(5);
        assertEquals(mockedList.size(), 5);

        verify(mockedList, atLeastOnce()).add("one");
        verify(mockedList, times(1)).add("two");
        verify(mockedList, times(3)).add("three times");
        verify(mockedList, never()).isEmpty();
    }

    @Test
    public void testCapture() {
        List<String> list = Arrays.asList("1", "2");
        List mockedList = mock(List.class);
        ArgumentCaptor<List> argument = ArgumentCaptor.forClass(List.class);
        mockedList.addAll(list);
        verify(mockedList).addAll(argument.capture());

        assertEquals(2, argument.getValue().size());
        assertEquals(list, argument.getValue());
    }


    @Test
    public void testLettuce() throws Exception {
        StatefulRedisConnection connection = redisClient.connect();
        RedisAsyncCommands commands = connection.async();
        commands.watch("default");
        commands.multi();
        commands.decr("default");
        RedisFuture<String> redisFuture = commands.get("default");
        RedisFuture execResult = commands.exec();
        String permits = redisFuture.get(100, TimeUnit.SECONDS);
        System.out.println(permits);
//        RedisCommands commands = connection.sync();
//        commands.set("name", "vico");
        connection.close();
        redisClient.shutdown();
    }

    @Test
    public void testRedisBasedRatelimiter() {
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(10)
                .limitRefreshPeriod(Duration.ofSeconds(150))
                //not wait long time , we can handle it using the dead-letter queue
                .timeoutDuration(Duration.ofMillis(100L))
                .build();
        RateLimiter rateLimiter = new RedisBasedRateLimiter("limiter", rateLimiterConfig);
//        Supplier supplier = RateLimiter.decorateSupplier(rateLimiter, () -> {
//            System.out.println("---");
//            return 1;
//        });
//        for (int i = 0; i < 3; i++) {
//            System.out.println(Try.ofSupplier(supplier)
//                    .onFailure((throwable) -> System.out.println("error!"))
//                    .onSuccess((result) -> System.out.println("success and the result is : " + result)));
//        }
    }


    @Test
    public void testMethod() {
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisAsyncCommands<String, String> commands = connection.async();
            RedisFuture<String> redisFuture = commands.get("hello");
            String permits = redisFuture.get(5, TimeUnit.NANOSECONDS);
            if (permits != null && Integer.valueOf(permits) > 0) {
                commands.decr("hello");
            } else {
                System.out.println("out of usage");
            }
        } catch (Exception e) {
            System.out.println("error");
        } finally {
            this.redisClient.shutdown();
        }
    }


    @Test
    public void testLettuceAsync() throws Exception {
        Boolean success = true;
        try (StatefulRedisConnection<String, String> connection = this.redisClient.connect()) {
            RedisAsyncCommands<String, String> commands = connection.async();
            RedisFuture<String> redisFuture = commands.get("limiter");
            String permits = redisFuture.get(Duration.ofMillis(100L).toMillis(), TimeUnit.MILLISECONDS);
            if (permits != null && Integer.valueOf(permits) > 0) {
                commands.decr("limiter");
            } else {
                success = false;
            }
        } catch (Exception e) {
            throw e;
        } finally {
            this.redisClient.shutdown();
            System.out.println(success);
        }
    }
}

