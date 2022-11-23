package com.salesforce.dockerfileimageupdate.utils;

import org.testng.annotations.Test;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import java.time.Duration;
import java.lang.InterruptedException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Callable;
import java.util.Optional;

public class RateLimiterTest {

    @Test
    public void testing() throws Exception {
        FutureTask futureTask = new FutureTask(new RateLimiterCallable());
        Thread t1 = new Thread(futureTask);
        t1.start();

        Thread.sleep(3000);
        // the token should be consumed within first 3s leaving the thread in blocked state as it'd be waiting for
        // next token to be available
        assertEquals(t1.getState().toString(), "TIMED_WAITING");
        Thread.sleep(20000);
        assertEquals(t1.getState().toString(), "TERMINATED");
        assertEquals(futureTask.get(), Optional.ofNullable(null));
    }
}

class RateLimiterCallable implements Callable {

    @Test
    @Override
    public Optional<Exception> call() {
        try {

            RateLimiter rateLimiter = new RateLimiter(2, Duration.ofSeconds(20),
                Duration.ofSeconds(10));
            rateLimiter.consume();
            Thread t = Thread.currentThread();
            assertEquals(t.getState().toString(), "RUNNABLE");
            //thread will go in waiting state as the second token will be available only after 10s
            rateLimiter.consume();
            return Optional.ofNullable(null);

        } catch (InterruptedException e) {
            System.out.println("exception thrown");
            return Optional.ofNullable(new InterruptedException());
        }
    }
}
