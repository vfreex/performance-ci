package org.jenkinsci.plugins.perfci.executor;

import java.io.IOException;

/**
 * Created by vfreex on 11/27/15.
 */
public abstract class AbstractExternalProgramExecutor {
    public abstract int run() throws IOException, InterruptedException;
}
