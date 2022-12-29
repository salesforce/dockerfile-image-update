package com.salesforce.dockerfileimageupdate.utils;

import com.google.common.collect.ImmutableMap;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import net.sourceforge.argparse4j.inf.Namespace;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class RateLimiterTest {

    @DataProvider(name = "dataForTestingRateLimits")
    public static Object[][] getRateLimitingData() {
        return new Object[][]{{2, 10, 5}, {4, 16, 4}};
    }

    @DataProvider(name = "dataForTestingRateLimitingEventCreation")
    public static Object[][] getDataForTestingRateLimitingEventCreation() {
        return new Object[][]{
                {"1234-per-2h", RateLimiter.class, false},
                {"500", RateLimiter.class, false},
                {"500-per-s", RateLimiter.class, false},
                {"500-per-60s", RateLimiter.class, false},
                {"image", null, true},
                {"500-random", null, true},
                {"", null, true},
                {"null", null, true}
        };
    }

    /**
     * This test is to test the rate Limiter against the wall clock.
     * This will run at least the rate limit seconds.
     */
    @Test(dataProvider = "dataForTestingRateLimits")
    public void testingAgainstWallClock(int rateLimit, int durationLimit, int tokenAddingRate) throws Exception {
        FutureTask futureTask = new FutureTask(new RateLimiterCallable
                (new RateLimitWrapper(null, rateLimit, durationLimit, tokenAddingRate)));
        Thread t1 = new Thread(futureTask);
        t1.start();
        Thread.sleep(100);
        // the token should be consumed within first 3s leaving the thread in blocked state as it'd be waiting for
        // next token to be available
        assertEquals(t1.getState().toString(), Thread.State.TIMED_WAITING.toString());
        t1.join();
        assertEquals(t1.getState().toString(), Thread.State.TERMINATED.toString());
        assertEquals(futureTask.get(), Optional.ofNullable(null));
    }

    /**
     * Using Custom Clock to increment clock counter
     * Instead of 60 seconds this should take < second to get completed
     *
     * @throws Exception
     */
    @Test(dataProvider = "dataForTestingRateLimits")
    public void testingAgainstCustomClock(int rateLimit, int durationLimit, int tokenAddingRate) throws Exception {
        RateLimitWrapper event = new RateLimitWrapper
                (new MockTimeMeter(), rateLimit, durationLimit, tokenAddingRate);
        FutureTask futureTask = new FutureTask(new RateLimiterCallable(event));
        Thread t1 = new Thread(futureTask);
        long startTime = System.currentTimeMillis();
        t1.start();
        t1.join();
        long endTime = System.currentTimeMillis();
        assertTrue((endTime - startTime) < (tokenAddingRate * 1_000L));
        assertEquals(event.getProcessedEventCount(), rateLimit);
        assertEquals(t1.getState().toString(), Thread.State.TERMINATED.toString());
        assertEquals(futureTask.get(), Optional.ofNullable(null));
    }

    @Test(dataProvider = "dataForTestingRateLimitingEventCreation")
    public void testGetRateLimiter(String envVariableVal, Class expectedReturnType, boolean isnull) {
        Map<String, Object> nsMap = ImmutableMap.of(
                Constants.RATE_LIMIT_PR_CREATION, envVariableVal);
        Namespace ns = new Namespace(nsMap);
        RateLimiter rateLimiter = new RateLimiter().getRateLimiter(ns);

        if (isnull) {
            assertNull(rateLimiter);
        } else {
            assertNotNull(rateLimiter);
            assertEquals(rateLimiter.getClass(), expectedReturnType);
        }
    }
}


class RateLimiterCallable implements Callable {
    RateLimitWrapper rateLimitWrapper;

    RateLimiterCallable(RateLimitWrapper rateLimitWrapper) {
        this.rateLimitWrapper = rateLimitWrapper;
    }

    @Override
    public Optional<Exception> call() {
        try {
            RateLimiter rateLimiter = new RateLimiter(rateLimitWrapper.ratelimit, Duration.ofSeconds(rateLimitWrapper.durationLimit),
                    Duration.ofSeconds(rateLimitWrapper.tokenAddingRate), rateLimitWrapper.getClock());
            int counter = 0;
            while (counter++ < rateLimitWrapper.ratelimit) {
                rateLimiter.consume();
                rateLimitWrapper.incrementEventProcessed();
                assertEquals(Thread.currentThread().getState().toString(), "RUNNABLE");
                if (rateLimitWrapper.getClock() != null) {
                    rateLimitWrapper.getClock().addSeconds(rateLimitWrapper.tokenAddingRate);
                }
            }
            return Optional.ofNullable(null);
        } catch (InterruptedException e) {
            return Optional.ofNullable(new InterruptedException());
        }
    }
}

class RateLimitWrapper {
    final MockTimeMeter clock;
    final long ratelimit;
    final long durationLimit;
    final long tokenAddingRate;
    final AtomicInteger processedEventCount = new AtomicInteger();

    public RateLimitWrapper(MockTimeMeter clock, long ratelimit, long durationLimit, long tokenAddingRate) {
        this.clock = clock;
        this.ratelimit = ratelimit;
        this.durationLimit = durationLimit;
        this.tokenAddingRate = tokenAddingRate;
    }

    public void incrementEventProcessed() {
        processedEventCount.incrementAndGet();
    }

    public int getProcessedEventCount() {
        return processedEventCount.get();
    }

    public MockTimeMeter getClock() {
        return clock;
    }
}

class MockTimeMeter implements TimeMeter {
    private long currentTimeNanos;

    public MockTimeMeter() {
        currentTimeNanos = 0;
    }

    public void addSeconds(long seconds) {
        currentTimeNanos += (seconds * 1_000_000_000);
    }

    @Override
    public long currentTimeNanos() {
        return currentTimeNanos;
    }

    @Override
    public boolean isWallClockBased() {
        return false;
    }
}


