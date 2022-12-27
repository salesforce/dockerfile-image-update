/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.utils;


import java.time.Duration;

/**
 * @author minho-park
 */
public class Constants {

    /* Should never be instantiated. */
    private Constants() {
    }

    public static final String COMMAND = "command";
    public static final String GIT_REPO = "<GIT_REPO>";
    public static final String STORE = "<IMG_TAG_STORE>";
    public static final String IMG = "<IMG>";
    public static final String TAG = "<TAG>";
    public static final String FORCE_TAG = "<FORCE_TAG>";
    public static final String GIT_API = "ghapi";
    public static final String GIT_ORG = "org";
    public static final String GIT_BRANCH = "branch";
    public static final String PULL_REQ_ID = "f9ed6ea5-6e74-4338-a629-50c5c6807a6b";
    public static final String STORE_JSON_FILE = "store.json";
    public static final String GIT_AUTO_MERGE = "f";
    public static final String GIT_PR_TITLE = "m";
    public static final String GIT_PR_BODY = "B";
    public static final String GIT_ADDITIONAL_COMMIT_MESSAGE = "c";
    public static final String GIT_REPO_EXCLUDES = "excludes";
    public static final String GIT_API_SEARCH_LIMIT = "ghapisearchlimit";
    public static final String SKIP_PR_CREATION = "skipprcreation";
    public static final String IGNORE_IMAGE_STRING = "x";
    public static final String USE_RATE_LIMITING = "useRatelimiting";
    public static final String RATE_LIMIT = "rateLimit";
    public static final String RATE_LIMIT_DURATION = "rateLimitDuration";
    public static final String TOKEN_ADDING_RATE = "tokenAddingRate";
    //max number of PRs to be sent (or tokens to be added)  per DEFAULT_RATE_LIMIT_DURATION(per hour in this case)
    public static final long DEFAULT_RATE_LIMIT = 30;
    public static final Duration DEFAULT_RATE_LIMIT_DURATION = Duration.ofHours(1);
    //token adding rate(here:a token added every 2 mins in the bucket)
    public static final Duration DEFAULT_TOKEN_ADDING_RATE = Duration.ofMinutes(2);

}
