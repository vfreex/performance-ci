package org.jenkinsci.plugins.perfci.common;

/**
 * Created by vfreex on 11/25/15.
 */
public interface LogDirectoryRelocatable {
    String getLogDirectory();
    void setLogDirectory(String logDirectory);
}
