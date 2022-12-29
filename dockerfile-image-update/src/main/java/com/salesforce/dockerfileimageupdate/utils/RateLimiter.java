package com.salesforce.dockerfileimageupdate.utils;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.UnknownFormatConversionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.lang3.math.NumberUtils;
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
    private final long rateLimit;
    private final Duration rateLimitDuration;
    private final Duration tokenAddingRate;
    private final Bucket bucket;

    public RateLimiter() {
        this(Constants.DEFAULT_RATE_LIMIT, Constants.DEFAULT_RATE_LIMIT_DURATION,
                Constants.DEFAULT_TOKEN_ADDING_RATE);
    }

    /**
     * constructor to initialize RateLimiter with required arguments.
     * If time meter is specified, the system default will be chosen.
     *
     * @param rLimit          maximum count of PRs to be sent per rLimitDuration
     * @param rLimitDuration  Rate Limit duration
     * @param tokAddingRate   Rate at which tokens are added in the bucket
     * @param customTimeMeter Clock to be used for bucketing the tokens.
     *                        Defaults to TimeMeter.SYSTEM_MILLISECONDS
     * @param <T>             Implementing class for customTimeMeter must be a class
     *                        extending TimeMeter
     * @see TimeMeter
     * @see TimeMeter#SYSTEM_MILLISECONDS
     */
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

    /**
     * This method will create and return an object of type RateLimiter based on the
     * variable set in the Namespace.
     *
     * @param ns Namespace {@see net.sourceforge.argparse4j.inf.Namespace}
     * @return RateLimiter object if required variable is set in Namespace
     *          null otherwise.
     * @see net.sourceforge.argparse4j.inf.Namespace Namespace
     */
    public RateLimiter getRateLimiter(Namespace ns) {
        String rateLimitPRCreation = ns.get(Constants.RATE_LIMIT_PR_CREATION);
        if (rateLimitPRCreation != null) {
            RateLimitEvent rlEvent;
            try {
                rlEvent = new RateLimitEvent().tokenizeAndGetEvent(rateLimitPRCreation);
            } catch (UnknownFormatConversionException ex) {
                log.error("Failed to parse ratelimiting argument, will not impose any rate limits", ex);
                return null;
            }
            log.info("Use rateLimiting is enabled, the PRs will be throttled in this run..");
            return new RateLimiter(rlEvent.rateLimit, rlEvent.rateLimitDuration, rlEvent.tokenAddingRate);
        }
        log.info("Use rateLimiting is disabled, the PRs will not be throttled in this run..");
        return null;
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
        bucket.asBlocking().consume(1);
    }

    /**
     * Creating this as inner class as existence of this class outside
     * RateLimiter class will make a little sense. This class is solely
     * meant to encapsulate the primitives needed for the RateLimiter
     * and logic for populating those after tokenizing the input
     */
    class RateLimitEvent {
        private final Pattern eventPattern =
                Pattern.compile("(^[1-9]\\d{0,18})(-per-)?([1-9]\\d{0,18})*([smh]?)", Pattern.CASE_INSENSITIVE);
        private final long rateLimit;
        private final Duration rateLimitDuration;
        private final Duration tokenAddingRate;

        public RateLimitEvent(long rateLimit, Duration rateLimitDuration, Duration tokenAddingRate) {
            this.rateLimit = rateLimit;
            this.rateLimitDuration = rateLimitDuration;
            this.tokenAddingRate = tokenAddingRate;
        }

        public RateLimitEvent() {
            this(0, null, null);
        }

        public long getRateLimit() {
            return rateLimit;
        }

        public Duration getRateLimitDuration() {
            return rateLimitDuration;
        }

        public Duration getTokenAddingRate() {
            return tokenAddingRate;
        }

        public RateLimitEvent tokenizeAndGetEvent(String input) {

            if (input == null) {
                throw new UnknownFormatConversionException("Can not tokenize/parse a null object");
            }

            long rLimit;
            long duration;
            Duration rLimitDuration;
            Duration tokAddingRate;
            String literalConstant;
            String rateDuration;
            String rateDurationUnitChar;

            Matcher matcher = eventPattern.matcher(input);

            if (!matcher.matches()) {
                throw new UnknownFormatConversionException("input argument is not in valid format and can't be tokenize");
            } else {
                /**
                 * eventPattern regex is divided into 5 groups
                 * 0 group being the whole expression
                 * 1 group is numeric rate limit value and will always
                 *                          be present if exp matched
                 * 2 is literal constant '-per-' and is optional
                 * 3 is numeric rate duration and is optional
                 * 4 is a char(s/m/h) and is optional
                 */
                rLimit = Long.parseLong(matcher.group(1)); // will always have a numeric value that fits in Long
                literalConstant = matcher.group(2); // can be null or empty or '-per-'
                rateDuration = matcher.group(3);// can be null or empty or numeric value
                rateDurationUnitChar = matcher.group(4); //can be empty or char s,m,h
            }

            if (literalConstant == null || literalConstant.isEmpty()) {
                //return rate with default duration and token adding rate
                return new RateLimitEvent(rLimit, Constants.DEFAULT_RATE_LIMIT_DURATION, Constants.DEFAULT_TOKEN_ADDING_RATE);
            }

            // value is either going to be a valid number or null. defaulting to 1 unit(every hour/every min and so on)
            duration = NumberUtils.isParsable(rateDuration) ? Long.parseLong(rateDuration) : 1;

            switch (rateDurationUnitChar) {
                case "s":
                case "S":
                    rLimitDuration = Duration.ofSeconds(duration);
                    tokAddingRate = Duration.ofSeconds(rLimit / duration);
                    break;
                case "m":
                case "M":
                    rLimitDuration = Duration.ofMinutes(duration);
                    tokAddingRate = Duration.ofMinutes(rLimit / duration);
                    break;
                case "h":
                case "H":
                    rLimitDuration = Duration.ofHours(duration);
                    tokAddingRate = Duration.ofHours(rLimit / duration);
                    break;
                default:
                    //should not reach here are regex will enforce char, keeping it for any unexpected use case.
                    throw new UnknownFormatConversionException("Unexpected format encountered, can't tokenize input");
            }
            return new RateLimitEvent(rLimit, rLimitDuration, tokAddingRate);
        }
    }
}
