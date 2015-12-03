package org.jenkinsci.plugins.perfci.common;

/**
 * Created by vfreex on 11/25/15.
 */
public interface ResultDirectoryRelocatable {
    String getResultDirectory();
    void setResultDirectory(String resultDirectory);
}
