/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface SubCommand {
    String help() default "";
    String[] requiredParams() default "";
    /* For optional parameters, add in an order so that the tag is before the parameter.
     * For example, optionalParams={"s", "store", "h", "hello"} will make the usage
     * [-s store] [-h hello].
     */
    String[] optionalParams() default "";
    boolean ignore() default false;
}
