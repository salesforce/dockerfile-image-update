package com.salesforce.dockerfileimageupdate.utils;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;

//based on token-bucket algorithm
public class RateLimiter {

    private long rateLimit;
    private Duration rateLimitDuration;
    private Duration tokenAddingRate;
    private Bucket bucket;
    
    public RateLimiter() {
        this(Constants.DEFAULT_RATE_LIMIT, Constants.DEFAULT_RATE_LIMIT_DURATION, Constants.DEFAULT_TOKEN_ADDING_RATE);
    }

    public RateLimiter(long rateLimit, Duration rateLimitDuration, Duration tokenAddingRate) {
        this.rateLimit = rateLimit;
        this.rateLimitDuration = rateLimitDuration;
        this.tokenAddingRate = tokenAddingRate;
        setup();
    }

    public void setup() {
        //refill the bucket at the end of every 'period' with 'rateLimit' tokens, not exceeding the max capacity
        Refill refill = Refill.intervally(rateLimit, rateLimitDuration);
        //initially bucket will have no. of tokens equal to its max capacity i.e the value of 'rateLimit'
        Bandwidth limit = Bandwidth.classic(rateLimit, refill);
        this.bucket = Bucket.builder()
                .addLimit(limit)
                //this is internal limit to avoid spikes and distribute the load uniformly over
                // DEFAULT_RATE_LIMIT_DURATION
                //one token added per DEFAULT_TOKEN_ADDING_RATE
                .addLimit(Bandwidth.classic(1, Refill.intervally(1, tokenAddingRate)))
                .build();
    }

    public void consume() throws InterruptedException {
        bucket.asBlocking().consume(1);
    }

}