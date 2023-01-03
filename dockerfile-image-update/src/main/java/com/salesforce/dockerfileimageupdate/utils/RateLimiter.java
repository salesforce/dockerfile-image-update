package com.salesforce.dockerfileimageupdate.utils;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.github.bucket4j.TimeMeter;
import java.util.Optional;
import java.util.UnknownFormatConversionException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class uses <a href="https://en.wikipedia.org/wiki/Token_buckete">Token bucket</a>
 * algorithm to evenly process the available resources in a given amount of time.
 * <pre>
 * With every PR sent by DFIU tool, a token will be consumed. This will ensure that a PR will
 * be raised only when there is a token left in the bucket. Initially bucket will have a fixed
 * number of tokens equal to the 'rateLimit' value. The bucket will be refilled with tokens at
 * the end of every 'rateLimitDuration' with 'rateLimit' tokens ensuring that at any point of
 * time, the number of tokens in the bucket won't exceed the 'rateLimit', limiting the rate at
 * which PRs would be raised. To keep the refill rate uniform, one token will be added to the
 * bucket every 'tokenAddingRate'. If no tokens are left in the bucket, then PR won't be raised
 * and program will halt until next token is available.
 * <pre>
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private final Bucket bucket;

    /**
     * constructor to initialize RateLimiter with required values.
     * If time meter is not specified, the system default will be chosen.
     *
     * @param customTimeMeter Clock to be used for bucketing the tokens.
     *                        Defaults to TimeMeter.SYSTEM_MILLISECONDS
     * @param <T>             Implementing class for customTimeMeter must be a class
     *                        extending TimeMeter
     * @see TimeMeter
     * @see TimeMeter#SYSTEM_MILLISECONDS
     */
    public <T extends TimeMeter> RateLimiter(RateLimit rateLimit, T customTimeMeter) {
        customTimeMeter = customTimeMeter != null ? customTimeMeter : (T) TimeMeter.SYSTEM_MILLISECONDS;
        // refill the bucket at the end of every 'rateLimitDuration' with 'rateLimit' tokens,
        // not exceeding the max capacity
        Refill refill = Refill.intervally(rateLimit.getRate(), rateLimit.getDuration());
        // initially bucket will have no. of tokens equal to its max capacity i.e.
        // the value of 'rateLimit'
        Bandwidth limit = Bandwidth.classic(rateLimit.getRate(), refill);
        this.bucket = Bucket.builder()
                .addLimit(limit)
                // this is internal limit to avoid spikes and distribute the load uniformly over
                // DEFAULT_RATE_LIMIT_DURATION
                // one token added per DEFAULT_TOKEN_ADDING_RATE
                .addLimit(Bandwidth.classic(1, Refill.intervally(1, rateLimit.getTokenAddingRate())))
                .withCustomTimePrecision(customTimeMeter)
                .build();
    }

    public RateLimiter(RateLimit rateLimit) {
        this(rateLimit, TimeMeter.SYSTEM_MILLISECONDS);
    }

    public RateLimiter() {
        this(new RateLimit());
    }

    /**
     * This method will create and return an object of type RateLimiter based on the
     * variable set in the Namespace.
     *
     * @param ns Namespace {@see net.sourceforge.argparse4j.inf.Namespace}
     * @return RateLimiter object if required variable is set in Namespace
     * null otherwise.
     * @see net.sourceforge.argparse4j.inf.Namespace Namespace
     */
    public static Optional<RateLimiter> getRateLimiter(Namespace ns) {
        String rateLimitPRCreation = ns.get(Constants.RATE_LIMIT_PR_CREATION);
        RateLimit rl = null;
        if (rateLimitPRCreation != null) {
            try {
                rl = RateLimit.tokenizeAndGetRateLimit(rateLimitPRCreation);
                log.info("Use rateLimiting is enabled, the PRs will be throttled in this run..");
            } catch (UnknownFormatConversionException ex) {
                log.error("Failed to parse ratelimiting argument, will not impose any rate limits", ex);
            }
        }
        log.info("Use rateLimiting is disabled, the PRs will not be throttled in this run..");
        return Optional.ofNullable(new RateLimiter(rl));
    }

    /**
     * This method consumes a token from the bucked.
     * It blocks the execution when no token is available
     * for consumption.
     *
     * @throws InterruptedException when interrupted
     * @see RateLimiter#bucket
     */
    public void consume() throws InterruptedException {
        bucket.asBlocking().consume(Constants.DEFAULT_CONSUMING_TOKEN_RATE);
    }
}
