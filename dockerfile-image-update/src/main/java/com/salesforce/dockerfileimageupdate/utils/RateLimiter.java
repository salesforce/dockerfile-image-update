package com.salesforce.dockerfileimageupdate.utils;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
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

    private RateLimiter() {
        this(Constants.DEFAULT_RATE_LIMIT, Constants.DEFAULT_RATE_LIMIT_DURATION,
                Constants.DEFAULT_TOKEN_ADDING_RATE);
    }

    private RateLimiter(long rateLimit, Duration rateLimitDuration, Duration tokenAddingRate) {
        this.rateLimit = rateLimit;
        this.rateLimitDuration = rateLimitDuration;
        this.tokenAddingRate = tokenAddingRate;
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
                .build();
    }

    public static RateLimiter getRateLimiter(Namespace ns) {
        if (ns.get(Constants.USE_RATE_LIMITING)) {
            log.info("Use rateLimiting is enabled, the PRs will be throttled in this run..");
            long rateLimit = Constants.DEFAULT_RATE_LIMIT;
            Duration rateLimitDuration = Constants.DEFAULT_RATE_LIMIT_DURATION;
            Duration tokenAddingRate = Constants.DEFAULT_TOKEN_ADDING_RATE;

            if (ns.get(Constants.RATE_LIMIT) != null) {
                rateLimit = ns.get(Constants.RATE_LIMIT);
            }
            if (ns.get(Constants.RATE_LIMIT_DURATION) != null) {
                rateLimitDuration = Duration.parse(ns.get(Constants.RATE_LIMIT_DURATION));
            }
            if (ns.get(Constants.TOKEN_ADDING_RATE) != null) {
                tokenAddingRate = Duration.parse(ns.get(Constants.TOKEN_ADDING_RATE));
            }
            return new RateLimiter(rateLimit, rateLimitDuration, tokenAddingRate);
        }
        log.info("Use rateLimiting is disabled, the PRs will not be throttled in this run..");
        return null;
    }

    public void consume() throws InterruptedException {
        bucket.asBlocking().consume(1);
    }

}
