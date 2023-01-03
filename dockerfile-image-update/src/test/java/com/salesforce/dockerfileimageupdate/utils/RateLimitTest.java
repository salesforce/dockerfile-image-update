package com.salesforce.dockerfileimageupdate.utils;

import java.time.Duration;
import java.util.UnknownFormatConversionException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class RateLimitTest {
    @DataProvider(name = "rateLimitInputSuccessData")
    public static Object[][] rateLimitInputSuccessData() {
        return new Object[][]{
                {"500", 500, Constants.DEFAULT_RATE_LIMIT_DURATION, Constants.DEFAULT_TOKEN_ADDING_RATE},
                {"500-per-s", 500, Duration.ofSeconds(1), Duration.ofSeconds(500)},
                {"500-per-60s", 500, Duration.ofSeconds(60), Duration.ofSeconds(500 / 60)},
                {"500-per-1m", 500, Duration.ofMinutes(1), Duration.ofMinutes(500)},
                {"500-per-1h", 500, Duration.ofHours(1), Duration.ofHours(500)},
                {"1234-per-2h", 1234, Duration.ofHours(2), Duration.ofHours(1234 / 2)},
        };
    }

    @DataProvider(name = "rateLimitInputFailureData")
    public static Object[][] rateLimitInputFailureData() {
        return new Object[][]{
                {"0", UnknownFormatConversionException.class},
                {"1234-per-2d", UnknownFormatConversionException.class},
                {"500-per-", UnknownFormatConversionException.class},
                {"500-random", UnknownFormatConversionException.class},
                {"random", UnknownFormatConversionException.class},
                {"", UnknownFormatConversionException.class},
                {null, UnknownFormatConversionException.class},
                {"2dse2", UnknownFormatConversionException.class},
                {"null", UnknownFormatConversionException.class},

        };
    }

    @Test(dataProvider = "rateLimitInputFailureData")
    public void testingFailureTokenizingOfInputString(String rateLimitInputStr, Class ex) {
        assertThrows(ex, () -> RateLimit.tokenizeAndGetRateLimit(rateLimitInputStr));
    }

    @Test(dataProvider = "rateLimitInputSuccessData")
    public void testingSuccessTokenizingOfInputString(String rateLimitInputStr, int rateLimitInputArg, Duration rateLimitDurationInputArg, Duration tokenAddingRateInputArg) {
        RateLimit rateLimit = RateLimit.tokenizeAndGetRateLimit(rateLimitInputStr);
        assertEquals(rateLimit.getRate(), rateLimitInputArg);
        assertEquals(rateLimit.getDuration(), rateLimitDurationInputArg);
        assertEquals(rateLimit.getTokenAddingRate(), tokenAddingRateInputArg);
    }
}
