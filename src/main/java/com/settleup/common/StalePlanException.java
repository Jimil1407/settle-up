package com.settleup.common;

import lombok.Getter;

/**
 * 409 - the client acted on a settlement plan that was computed before someone else changed the
 * group's balances. The client should re-fetch the plan rather than blindly retry.
 */
@Getter
public class StalePlanException extends RuntimeException {

    private final long expectedVersion;
    private final long currentVersion;

    public StalePlanException(long expectedVersion, long currentVersion) {
        super("settlement plan was computed at version " + expectedVersion
                + " but the group is now at version " + currentVersion
                + "; re-fetch the plan and try again");
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }
}
