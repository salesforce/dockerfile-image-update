package com.salesforce.dockerfileimageupdate.utils;

import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RateLimiterTest {

    @DataProvider(name = "rateLimitingData")
    public static Object[][] primeNumbers() {
        return new Object[][]{{2, 10, 5}, {4, 16, 4}};
    }

    @Test(dataProvider = "rateLimitingData")
    public void testingAgainstWallClock(int rateLimit, int durationLimit, int tokenAddingRate) throws Exception {
        FutureTask futureTask = new FutureTask(new RateLimiterCallable
                (new RateLimiterEvent(null, rateLimit, durationLimit, tokenAddingRate)));
        Thread t1 = new Thread(futureTask);
        long startTime = System.currentTimeMillis();
        t1.start();
        Thread.sleep(1000);
        // the token should be consumed within first 3s leaving the thread in blocked state as it'd be waiting for
        // next token to be available
        assertEquals(t1.getState().toString(), "TIMED_WAITING");
        t1.join();
        long endTime = System.currentTimeMillis();
        assertTrue((endTime - startTime) > (tokenAddingRate * 1_000L));
        assertEquals(t1.getState().toString(), "TERMINATED");
        assertEquals(t1.getState().toString(), "TERMINATED");
        assertEquals(futureTask.get(), Optional.ofNullable(null));
    }

    /**
     * Using Custom Clock to increment clock counter
     * Instead of 60 seconds this should take < second to get completed
     *
     * @throws Exception
     */
    @Test(dataProvider = "rateLimitingData")
    public void testingAgainstCustomClock(int rateLimit, int durationLimit, int tokenAddingRate) throws Exception {
        RateLimiterEvent event = new RateLimiterEvent
                (new CustomTimeMeter(), rateLimit, durationLimit, tokenAddingRate);
        FutureTask futureTask = new FutureTask(new RateLimiterCallable(event));
        Thread t1 = new Thread(futureTask);
        long startTime = System.currentTimeMillis();
        t1.start();
        t1.join();
        long endTime = System.currentTimeMillis();
        assertTrue((endTime - startTime) < (tokenAddingRate * 1_000L));
        assertEquals(event.getProcessedEventCount(), rateLimit);
        assertEquals(t1.getState().toString(), "TERMINATED");
        assertEquals(futureTask.get(), Optional.ofNullable(null));
    }
}


class RateLimiterCallable implements Callable {
    RateLimiterEvent rateLimiterEvent;

    RateLimiterCallable(RateLimiterEvent rateLimiterEvent) {
        this.rateLimiterEvent = rateLimiterEvent;
    }

    @Test
    @Override
    public Optional<Exception> call() {
        try {
            RateLimiter rateLimiter = new RateLimiter(rateLimiterEvent.ratelimit, Duration.ofSeconds(rateLimiterEvent.durationLimit),
                    Duration.ofSeconds(rateLimiterEvent.tokenAddingRate), RateLimiterEvent.clock);
            int counter = 0;
            while (counter++ < rateLimiterEvent.ratelimit) {
                rateLimiter.consume();
                rateLimiterEvent.incrementEventProcessed();
                assertEquals(Thread.currentThread().getState().toString(), "RUNNABLE");
                if (RateLimiterEvent.clock != null) {
                    RateLimiterEvent.clock.addSeconds(rateLimiterEvent.tokenAddingRate);
                }
            }
            return Optional.ofNullable(null);
        } catch (InterruptedException e) {
            System.out.println("exception thrown");
            return Optional.ofNullable(new InterruptedException());
        }
    }
}


class RateLimiterEvent {
    static CustomTimeMeter clock;
    long ratelimit;
    long durationLimit;
    long tokenAddingRate;
    volatile int processedEventCount = 0;

    public RateLimiterEvent(CustomTimeMeter clock, long ratelimit, long durationLimit, long tokenAddingRate) {
        RateLimiterEvent.clock = clock;
        this.ratelimit = ratelimit;
        this.durationLimit = durationLimit;
        this.tokenAddingRate = tokenAddingRate;
    }

    public void incrementEventProcessed() {
        this.processedEventCount++;
    }

    public int getProcessedEventCount() {
        return processedEventCount;
    }
}

class CustomTimeMeter implements TimeMeter {
    private volatile long currentTimeNanos;

    public CustomTimeMeter() {
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


