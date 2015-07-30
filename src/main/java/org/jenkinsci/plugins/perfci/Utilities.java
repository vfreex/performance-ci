package org.jenkinsci.plugins.perfci;

import java.util.Iterator;

public class Utilities {
    public static String joinArray(String separator, Iterable<?> parts) {
        Iterator<?> it = parts.iterator();
        if (!it.hasNext())
            return "";
        StringBuilder sb = new StringBuilder(it.next().toString());
        while (it.hasNext()) {
            sb.append(separator).append(it.next().toString());
        }
        return sb.toString();
    }
}
