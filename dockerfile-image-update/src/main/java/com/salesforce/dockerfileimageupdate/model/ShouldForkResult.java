package com.salesforce.dockerfileimageupdate.model;


import com.google.common.base.Objects;

/**
 * A result which can help determine if a repo should be forked or not.
 * If it should not be forked, comes with a reason.
 */
public class ShouldForkResult {
    public static final String NO_REASON = "no reason. Repo can be forked.";
    private final boolean shouldFork;
    private final String reason;

    private ShouldForkResult(boolean shouldFork, String reason) {
        this.shouldFork = shouldFork;
        this.reason = reason;
    }

    public static ShouldForkResult shouldForkResult() {
        return new ShouldForkResult(true, NO_REASON);
    }

    public static ShouldForkResult shouldNotForkResult(String reason) {
        return new ShouldForkResult(false, reason);
    }


    /**
     * Allows for chaining ShouldForkResult instances such that and() will
     * return the first ShouldForkResult which results in isForkable() == false.
     * @param otherShouldForkResult the other ShouldForkResult to return if this is forkable.
     * @return chainable {@code ShouldForkResult}
     */
    public ShouldForkResult and(ShouldForkResult otherShouldForkResult) {
        if (isForkable()) {
            return otherShouldForkResult;
        }
        return this;
    }

    public boolean isForkable() {
        return shouldFork;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShouldForkResult that = (ShouldForkResult) o;
        return shouldFork == that.shouldFork &&
                Objects.equal(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(shouldFork, reason);
    }

    @Override
    public String toString() {
        if (isForkable()) {
            return "ShouldForkResult[Is forkable]";
        }
        return String.format("ShouldForkResult[Not forkable because %s", getReason());
    }
}
