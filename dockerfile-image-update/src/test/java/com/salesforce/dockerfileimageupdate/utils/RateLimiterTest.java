package com.salesforce.dockerfileimageupdate.utils;

import org.testng.annotations.Test;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import java.time.Duration;
import java.lang.InterruptedException;

public class RateLimiterTest extends Thread{
    
    @Test
    public void testConsume() throws InterruptedException{
        Thread t1 = new Thread(){
            @Override
            public void run() {
                try {
                    RateLimiter rateLimiter = new RateLimiter(2,Duration.ofMinutes(2),Duration.ofMinutes(1));
                    rateLimiter.consume();
                    Thread t = Thread.currentThread();
                    assertEquals(t.getState().toString(),"RUNNABLE");
                    //thread will go in waiting state as the second token will be available only after 1 min
                    rateLimiter.consume();
                }catch(InterruptedException e){
                    System.out.println("exception thrown");
                }
            }
        };
        t1.start();
        Thread.sleep(30000);
        // the token should be consumed within first 30s leaving the thread in blocked state as it'd be waiting for 
        // next token to be available
        assertEquals(t1.getState().toString(),"TIMED_WAITING");
        Thread.sleep(40000);
        assertEquals(t1.getState().toString(),"TERMINATED");

    }
}