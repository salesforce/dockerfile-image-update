package com.salesforce.dockerfileimageupdate.utils;

import java.time.Duration;
import java.util.UnknownFormatConversionException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class RateLimitTest {
    @DataProvider(name = "rateLimitInputSuccessData")
    public static Object[][] rateLimitInputSuccessData() {
        return new Object[][]{
                {"500-PT1S", 500, "PT1S", "PT0.002S"},
                {"500-PT60S", 500, "PT1M", "PT0.12S"},
                {"500-PT1M", 500, "PT1M", "PT0.12S"},
                {"500-PT1H", 500, "PT1H", "PT7.2S"},
                {"500-PT6H", 500, "PT6H", "PT43.2S"},
                {"600-PT1H", 600, "PT1H", "PT6S"},
                {"86400-PT24H", 86400, "PT24H", "PT1S"},
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
                {"500-per-s", UnknownFormatConversionException.class},
                {"500-PT1H-PT1H", UnknownFormatConversionException.class},

        };
    }

    @Test(dataProvider = "rateLimitInputFailureData")
    public void testingFailureTokenizingOfInputString(String rateLimitInputStr, Class ex) {
        assertThrows(ex, () -> RateLimit.tokenizeAndGetRateLimit(rateLimitInputStr));
    }

    @Test(dataProvider = "rateLimitInputSuccessData")
    public void testingSuccessTokenizingOfInputString(String rateLimitInputStr, long rateLimitInputArg, String rlDuration, String rlTokAddingRate) {
        RateLimit rateLimit = RateLimit.tokenizeAndGetRateLimit(rateLimitInputStr);
        assertEquals(rateLimit.getRate(), rateLimitInputArg);
        assertEquals(rateLimit.getDuration(), Duration.parse(rlDuration));
        assertEquals(rateLimit.getTokenAddingRate(), Duration.parse(rlTokAddingRate));
    }
}
