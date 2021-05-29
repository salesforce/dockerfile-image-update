/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.utils;


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
}
