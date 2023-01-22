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
                {"500-PT1S", RateLimiter.class, false},
                {"500-PT60S", RateLimiter.class, false},
                {"500-PT1M", RateLimiter.class, false},
                {"500-PT1H", RateLimiter.class, false},
                {"image", null, true},
                {"500-random", null, true},
                {"", null, true},
                {"null", null, true},
                {"500", null, true}
        };
    }

    /**
     * This test is to test the rate Limiter against the wall clock.
     * This will run at least the rate limit seconds.
     */
    @Test(dataProvider = "dataForTestingRateLimits")
    public void testingAgainstWallClock(int rateLimit, int durationLimit, int tokenAddingRate) throws Exception {
        RateLimit rl = new RateLimit(rateLimit, Duration.ofSeconds(durationLimit), Duration.ofSeconds(tokenAddingRate));
        RateLimitTestEvent event = new RateLimitTestEvent(null, rl);
        FutureTask futureTask = new FutureTask(new RateLimiterCallable(event));
        Thread t1 = new Thread(futureTask);
        t1.start();
        t1.join();
        assertEquals(t1.getState().toString(), Thread.State.TERMINATED.toString());
        assertEquals(event.getProcessedEventCount().get(), rateLimit);
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
        RateLimit rl = new RateLimit(rateLimit, Duration.ofSeconds(durationLimit), Duration.ofSeconds(tokenAddingRate));
        RateLimitTestEvent event = new RateLimitTestEvent(new MockTimeMeter(), rl);
        FutureTask futureTask = new FutureTask(new RateLimiterCallable(event));
        Thread t1 = new Thread(futureTask);
        long startTime = System.currentTimeMillis();
        t1.start();
        t1.join();
        long endTime = System.currentTimeMillis();
        assertTrue((endTime - startTime) < (tokenAddingRate * 1_000L));
        assertEquals(event.getProcessedEventCount().get(), rateLimit);
        assertEquals(t1.getState().toString(), Thread.State.TERMINATED.toString());
        assertEquals(futureTask.get(), Optional.ofNullable(null));
    }

    @Test(dataProvider = "dataForTestingRateLimitingEventCreation")
    public void testGetRateLimiter(String envVariableVal, Class expectedReturnType, boolean isnull) {
        Map<String, Object> nsMap = ImmutableMap.of(
                Constants.RATE_LIMIT_PR_CREATION, envVariableVal);
        Namespace ns = new Namespace(nsMap);
        RateLimiter rateLimiter = RateLimiter.getInstance(ns);

        if (isnull) {
            assertNull(rateLimiter);
        } else {
            assertNotNull(rateLimiter);
            assertEquals(rateLimiter.getClass(), expectedReturnType);
        }
    }
}


class RateLimiterCallable implements Callable {
    RateLimitTestEvent rateLimitTestEvent;

    RateLimiterCallable(RateLimitTestEvent rateLimitTestEvent) {
        this.rateLimitTestEvent = rateLimitTestEvent;
    }

    @Override
    public Optional<Exception> call() {
        try {
            RateLimiter rateLimiter = new RateLimiter(rateLimitTestEvent.getRateLimit(), rateLimitTestEvent.getClock());
            int counter = 0;
            while (counter++ < rateLimitTestEvent.getRateLimit().getRate()) {
                rateLimiter.consume();
                rateLimitTestEvent.incrementEventProcessed();

                if (rateLimitTestEvent.getClock() != null) {
                    rateLimitTestEvent.getClock().addSeconds(rateLimitTestEvent.getRateLimit().getTokenAddingRate().getSeconds());
                }
            }
            return Optional.ofNullable(null);
        } catch (InterruptedException e) {
            return Optional.ofNullable(new InterruptedException());
        }
    }
}

class RateLimitTestEvent {
    final MockTimeMeter clock;
    final RateLimit rateLimit;
    final AtomicInteger processedEventCount = new AtomicInteger();

    public RateLimitTestEvent(MockTimeMeter clock, RateLimit rateLimit) {
        this.clock = clock;
        this.rateLimit = rateLimit;
    }

    public void incrementEventProcessed() {
        processedEventCount.incrementAndGet();
    }

    public MockTimeMeter getClock() {
        return clock;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public AtomicInteger getProcessedEventCount() {
        return processedEventCount;
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


