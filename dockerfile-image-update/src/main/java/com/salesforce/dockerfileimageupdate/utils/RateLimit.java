package com.salesforce.dockerfileimageupdate.utils;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.UnknownFormatConversionException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class meant to tokenize input and encapsulate the primitives needed for
 * the RateLimiter
 *
 * @see RateLimiter
 */
public class RateLimit {
    public static final String ERROR_MESSAGE = "Unexpected format or unit encountered, valid input is " +
            "<integer>-<ISO-8601_formatted_time> Example: 500-PT1S means(500 per second) \n" +
            "500-PT1M means(500 per Minute)";
    private static final Logger log = LoggerFactory.getLogger(RateLimit.class);
    private final long rate; //Maximum count of PRs to be sent per rLimitDuration
    private final Duration duration; //Rate Limit duration
    private final Duration tokenAddingRate; //Rate at which tokens are added in the bucket

    /**
     * Initialize RateLimit object with based on parameters passed
     *
     * @param rate            Maximum count of PRs to be sent per rLimitDuration
     * @param duration        Rate Limit duration
     * @param tokenAddingRate Rate at which tokens are added in the bucket
     */
    public RateLimit(long rate, Duration duration, Duration tokenAddingRate) {
        this.rate = rate;
        this.duration = duration;
        this.tokenAddingRate = tokenAddingRate;
    }

    /**
     * Initialize RateLimit object with defaults
     */
    public RateLimit() {
        this(Constants.DEFAULT_RATE_LIMIT, Constants.DEFAULT_RATE_LIMIT_DURATION,
                Constants.DEFAULT_TOKEN_ADDING_RATE);
    }

    /**
     * This method will accept an input string in format <integer>-<ISO-8601_formatted_time> and
     * will tokenize it to create a RateLimit object.
     * <pre>
     * Examples of tokenization
     * input : 500-PT1S -> {rate : 500, duration 1 second (PT1S), token adding rate: every .002 seconds (PT0.002S)}
     *         60-PT1M -> {rate : 60, duration 1 minute (PT1M), token adding rate: every 1 seconds (PT1S)}
     * </pre>
     *
     * @param input string in format <integer>-<ISO-8601 formatted_time]> example 500-PT1S , 500-PT60S , 500-PT1H
     * @return RateLimit based on the input value
     * @throws UnknownFormatConversionException if input is not a string with expected format <integer>-<ISO-8601_formatted_time>
     */
    public static RateLimit tokenizeAndGetRateLimit(String input) throws UnknownFormatConversionException {
        if (StringUtils.isEmpty(input) || !input.contains("-")) {
            throw new UnknownFormatConversionException(ERROR_MESSAGE);
        }

        int delimiterIndex = input.indexOf('-');

        try {
            long inputRate = Long.parseLong(input.substring(0, delimiterIndex));
//            "PT20.345S" -- parses as "20.345 seconds"
//            "PT15M"     -- parses as "15 minutes" (where a minute is 60 seconds)
//            "PT10H"     -- parses as "10 hours" (where an hour is 3600 seconds)
//            "P2D"       -- parses as "2 days" (where a day is 24 hours or 86400
            Duration inputDuration = Duration.parse(input.substring(delimiterIndex + 1));

//            Keeping token adding rate based on the lowest supported rate i.e. nanoseconds.
//            means new tokens can be added to the rate limiter every nanosecond based on
//            the input passed.
//            Duration is auto casted to upper unit of time. so input of 600-PT60M
//            will be set inputTokAddingRate to PT6S means every 6 seconds one token will be
//            added to the pool to be consumed
//
            Duration inputTokAddingRate = Duration.ofNanos(inputDuration.toNanos() / inputRate);

            log.info("constructing rate limit object with rate: {}, duration: {}, and token adding rate: {}",
                    inputRate, inputDuration, inputTokAddingRate);
            return new RateLimit(inputRate, inputDuration, inputTokAddingRate);
        } catch (NumberFormatException | DateTimeParseException ex) {
            throw new UnknownFormatConversionException(ERROR_MESSAGE);
        }

    }

    public long getRate() {
        return rate;
    }

    public Duration getDuration() {
        return duration;
    }

    public Duration getTokenAddingRate() {
        return tokenAddingRate;
    }
}
