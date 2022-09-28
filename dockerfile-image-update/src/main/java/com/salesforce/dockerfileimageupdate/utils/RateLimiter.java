package com.salesforce.dockerfileimageupdate.utils;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import java.time.Duration;

public class RateLimiter {

    private int rateLimit;
    private Bucket bucket;
    private ConsumptionProbe probe;

    public RateLimiter() {

        this.rateLimit = Constants.RATE_LIMIT;
        //refill the bucket at the end of every hour with 'rateLimit' tokens, not exceeding the max capacity
        Refill refill = Refill.intervally(rateLimit, Duration.ofHours(1));
        //initially bucket will have no. of tokens equal to its max capacity i.e the value of 'rateLimit'
        Bandwidth limit = Bandwidth.classic(rateLimit, refill);
        this.bucket = Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    public boolean checkLimit() {
        return bucket.tryConsume(1);
    }

}