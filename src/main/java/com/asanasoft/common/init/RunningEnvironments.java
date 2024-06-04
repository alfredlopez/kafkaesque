package com.asanasoft.common.init;

import java.util.Arrays;

public enum RunningEnvironments {
    LOCAL, DEV, UAT, PROD;

    public static boolean isEnvironmentBound(String stringToTest) {
        boolean found = false;
        found = !Arrays.stream(values()).noneMatch(env -> stringToTest.contains(env.toString()));
        return found;
    }
}
