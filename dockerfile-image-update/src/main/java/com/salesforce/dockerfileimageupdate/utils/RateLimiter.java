package com.salesforce.dockerfileimageupdate.utils;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by tarishij17
 * RateLimiter is based on token bucket algorithm.
 * With every PR sent by DFIU tool, a token will be consumed,
 * ensuring that a PR will be raised only when there is a token left in the bucket.
 * Initially bucket will have a fixed number of tokens equal to the 'rateLimit' value.
 * The bucket will be refilled with tokens at the end of every 'rateLimitDuration' with 'rateLimit' tokens
 * ensuring that at any point of time, the number of tokens in the bucket won't exceed the 'rateLimit' value,
 * thus limiting the rate at which PRs would be raised.
 * To keep the refill rate uniform, one token will be added to the bucket every 'tokenAddingRate'.
 * If no tokens are left in the bucket, then PR won't be raised and
 * program will halt until next token is available.
 */

//based on token-bucket algorithm
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private final long rateLimit;
    private final Duration rateLimitDuration;
    private final Duration tokenAddingRate;
    private final Bucket bucket;

    public RateLimiter() {
        this(Constants.DEFAULT_RATE_LIMIT, Constants.DEFAULT_RATE_LIMIT_DURATION,
                Constants.DEFAULT_TOKEN_ADDING_RATE);
    }

    public <T extends TimeMeter> RateLimiter(long rLimit, Duration rLimitDuration,
                                             Duration tokAddingRate, T customTimeMeter) {
        rateLimit = rLimit;
        rateLimitDuration = rLimitDuration;
        tokenAddingRate = tokAddingRate;
        customTimeMeter = customTimeMeter != null ? customTimeMeter : (T) TimeMeter.SYSTEM_MILLISECONDS;
        // refill the bucket at the end of every 'rateLimitDuration' with 'rateLimit' tokens,
        // not exceeding the max capacity
        Refill refill = Refill.intervally(rateLimit, rateLimitDuration);
        // initially bucket will have no. of tokens equal to its max capacity i.e
        // the value of 'rateLimit'
        Bandwidth limit = Bandwidth.classic(rateLimit, refill);
        this.bucket = Bucket.builder()
                .addLimit(limit)
                // this is internal limit to avoid spikes and distribute the load uniformly over
                // DEFAULT_RATE_LIMIT_DURATION
                // one token added per DEFAULT_TOKEN_ADDING_RATE
                .addLimit(Bandwidth.classic(1, Refill.intervally(1, tokenAddingRate)))
                .withCustomTimePrecision(customTimeMeter)
                .build();
    }

    public RateLimiter(long rateLimit, Duration rateLimitDuration,
                       Duration tokenAddingRate) {
        this(rateLimit, rateLimitDuration, tokenAddingRate, null);

    }

    public RateLimiter getRateLimiter(Namespace ns) {
        if (ns.get(Constants.USE_RATE_LIMITING)) {
            log.info("Use rateLimiting is enabled, the PRs will be throttled in this run..");
            long rLimit = ns.get(Constants.RATE_LIMIT) != null ? ns.get(Constants.RATE_LIMIT) : Constants.DEFAULT_RATE_LIMIT;
            Duration rLimitDuration = ns.get(Constants.RATE_LIMIT_DURATION) != null ? Duration.parse(ns.get(Constants.RATE_LIMIT_DURATION)) : Constants.DEFAULT_RATE_LIMIT_DURATION;
            Duration tokAddingRate = ns.get(Constants.TOKEN_ADDING_RATE) != null ? Duration.parse(ns.get(Constants.TOKEN_ADDING_RATE)) : Constants.DEFAULT_TOKEN_ADDING_RATE;
            return new RateLimiter(rLimit, rLimitDuration, tokAddingRate);
        }
        log.info("Use rateLimiting is disabled, the PRs will not be throttled in this run..");
        return null;
    }

    public void consume() throws InterruptedException {
        bucket.asBlocking().consume(1);
    }

}
