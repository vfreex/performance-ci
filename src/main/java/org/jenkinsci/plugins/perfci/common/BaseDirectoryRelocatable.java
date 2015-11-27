package org.jenkinsci.plugins.perfci.common;

/**
 * Created by vfreex on 11/25/15.
 */
public interface BaseDirectoryRelocatable {
    String getBaseDirectory();
    void setBaseDirectory(String baseDirectory);
}
