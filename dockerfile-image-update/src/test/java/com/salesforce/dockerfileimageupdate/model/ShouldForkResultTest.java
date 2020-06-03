package com.salesforce.dockerfileimageupdate.model;

import com.salesforce.dockerfileimageupdate.process.ForkableRepoValidator;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static com.salesforce.dockerfileimageupdate.model.ShouldForkResult.*;
import static com.salesforce.dockerfileimageupdate.process.ForkableRepoValidator.REPO_IS_ARCHIVED;
import static org.testng.Assert.*;

public class ShouldForkResultTest {
    @Test
    public void testShouldForkResult() {
        ShouldForkResult shouldForkResult = shouldForkResult();
        assertTrue(shouldForkResult.isForkable());
        assertEquals(shouldForkResult.getReason(), NO_REASON);
    }

    @DataProvider
    public Object[][] testEqualsData() {
        ShouldForkResult same = shouldForkResult();
        return new Object[][]{
                {same, same},
                {shouldForkResult(), shouldForkResult()},
                {shouldNotForkResult("test"), shouldNotForkResult("test")},
                {shouldNotForkResult(REPO_IS_ARCHIVED), shouldNotForkResult(REPO_IS_ARCHIVED)},
        };
    }

    @Test(dataProvider = "testEqualsData")
    public void testEquals(ShouldForkResult first, ShouldForkResult second) {
        assertEquals(first, second);
    }

    @Test
    public void testNotEqual() {
        assertNotEquals(shouldNotForkResult("another"), shouldNotForkResult("test"));
    }

    @Test
    public void testEqualHashCode() {
        assertEquals(shouldNotForkResult("test").hashCode(), shouldNotForkResult("test").hashCode());
    }

    @Test
    public void testNotEqualHashCode() {
        assertNotEquals(shouldNotForkResult("another").hashCode(), shouldNotForkResult("test").hashCode());
    }

    @DataProvider
    public Object[][] shouldNotForkReasons() {
        return new Object[][]{
                {ForkableRepoValidator.COULD_NOT_CHECK_THIS_USER},
                {ForkableRepoValidator.REPO_IS_FORK},
                {REPO_IS_ARCHIVED},
                {ForkableRepoValidator.REPO_IS_OWNED_BY_THIS_USER}
        };
    }

    @Test(dataProvider = "shouldNotForkReasons")
    public void testShouldNotForkResult(String reason) {
        ShouldForkResult shouldForkResult = shouldNotForkResult(reason);
        assertFalse(shouldForkResult.isForkable());
        assertEquals(shouldForkResult.getReason(), reason);
    }

    @DataProvider
    public Object[][] test2Ands() {
        return new Object[][]{
                {shouldForkResult(), shouldForkResult(), true, NO_REASON},
                {shouldForkResult(), shouldNotForkResult(REPO_IS_ARCHIVED), false, REPO_IS_ARCHIVED},
                {shouldNotForkResult(REPO_IS_ARCHIVED), shouldForkResult(), false, REPO_IS_ARCHIVED},
                {shouldNotForkResult(REPO_IS_ARCHIVED), shouldNotForkResult(ForkableRepoValidator.REPO_IS_FORK), false, REPO_IS_ARCHIVED},
        };
    }

    @Test(dataProvider = "test2Ands")
    public void testAnd(ShouldForkResult shouldForkResult, ShouldForkResult shouldForkResult2, boolean result, String reason) {
        assertEquals(shouldForkResult.and(shouldForkResult2).isForkable(), result);
        assertEquals(shouldForkResult.and(shouldForkResult2).getReason(), reason);
    }
}