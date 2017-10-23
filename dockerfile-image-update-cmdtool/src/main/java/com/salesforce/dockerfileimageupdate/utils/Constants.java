/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.utils;


/**
 *
 * @author minho-park
 *
 */
public class Constants {

    /* Should never be instantiated. */
    private Constants () { }

    public static final String COMMAND = "command";
    public static final String GIT_REPO = "<GIT_REPO>";
    public static final String STORE = "<IMG_TAG_STORE>";
    public static final String IMG = "<IMG>";
    public static final String TAG = "<TAG>";
    public static final String FORCE_TAG = "<FORCE_TAG>";
    public static final String GIT_API = "ghapi";
    public static final String BASE_IMAGE_INST = "FROM";
    public static final String PULL_REQ_ID = "f9ed6ea5-6e74-4338-a629-50c5c6807a6b";
    public static final String STORE_JSON_FILE = "store.json";
    public static final String GITHUB_FILE = "file";
}
