package com.salesforce.dva.dockerfileimageupdate;

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
