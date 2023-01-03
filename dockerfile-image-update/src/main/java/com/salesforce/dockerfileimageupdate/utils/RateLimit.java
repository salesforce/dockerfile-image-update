package com.salesforce.dockerfileimageupdate.utils;

import java.time.Duration;
import java.util.UnknownFormatConversionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * Creating this as inner class as existence of this class outside
 * RateLimiter class will make a little sense. This class is solely
 * meant to encapsulate the primitives needed for the RateLimiter
 * and logic for populating those after tokenizing the input
 */
public class RateLimit {
    public static final String errorMessage = "Unexpected format or unit encountered, valid input is " +
            "<integer>-per-<time-unit> where time-init is one of 's', 'm', or 'h'. " +
            "Example: 500-per-h";
    private static final Pattern eventPattern =
            Pattern.compile("(^[1-9]\\d{0,18})(-per-)?([1-9]\\d{0,18})*([smh]?)", Pattern.CASE_INSENSITIVE);
    private final long rate; //Maximum count of PRs to be sent per rLimitDuration
    private final Duration duration; //Rate Limit duration
    private final Duration tokenAddingRate; //Rate at which tokens are added in the bucket

    /**
     * Initialize RateLimit object with based on parameters passed
     * @param rate Maximum count of PRs to be sent per rLimitDuration
     * @param duration Rate Limit duration
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

    public static RateLimit tokenizeAndGetRateLimit(String input) {

        if (input == null) {
            throw new UnknownFormatConversionException(errorMessage);
        }

        long rLimit;
        Duration rLimitDuration;
        Duration tokAddingRate;
        String literalConstant;
        String rateDuration;
        String rateDurationUnitChar;

        Matcher matcher = eventPattern.matcher(input);

        if (!matcher.matches()) {
            throw new UnknownFormatConversionException(errorMessage);
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

        if (StringUtils.isEmpty(literalConstant)) {
            //return rate with default duration and token adding rate
            return new RateLimit(rLimit, Constants.DEFAULT_RATE_LIMIT_DURATION, Constants.DEFAULT_TOKEN_ADDING_RATE);
        }

        // value is either going to be a valid number or null. defaulting to 1 unit(every hour/every min and so on)
        long duration = NumberUtils.isParsable(rateDuration) ? Long.parseLong(rateDuration) : 1;

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
                throw new UnknownFormatConversionException(errorMessage);
        }
        return new RateLimit(rLimit, rLimitDuration, tokAddingRate);
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
