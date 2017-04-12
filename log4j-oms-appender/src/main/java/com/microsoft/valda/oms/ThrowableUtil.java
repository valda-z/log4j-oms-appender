package com.microsoft.valda.oms;

/*
 * Microsoft OMS log4j appender
 *
 * Copyright(c) Microsoft Corporation All rights reserved.
 *
 * This program is made available under the terms of the MIT License. See the LICENSE file in the project root for more information.
 */

import java.util.HashSet;
import java.util.Set;

public class ThrowableUtil {
    public static String getStacktrace(Throwable e) {

        Set<Throwable> used = new HashSet<Throwable>();
        StringBuilder sb = new StringBuilder();

        while (e != null && !used.contains(e)) {
            if (used.size() > 0) {
                sb.append("\n");
            }

            used.add(e);
            sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
            StackTraceElement[] elements = e.getStackTrace();

            for (StackTraceElement element : elements) {
                sb.append("    ").append(element.toString()).append("\n");
            }

            e = e.getCause();
        }

        return sb.toString();
    }
}
