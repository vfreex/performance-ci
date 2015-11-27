package org.jenkinsci.plugins.perfci.model;

import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.perfci.common.BaseDirectoryRelocatable;

import java.io.IOException;
import java.io.Serializable;

/**
 * Created by Rayson Zhu on 11/17/15.
 */
public abstract class PerformanceTester implements Describable<PerformanceTester>, Serializable, ExtensionPoint {
    public abstract static class PerformanceTesterDescriptor extends
            Descriptor<PerformanceTester> {
    }

    public abstract void run(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException;

    public PerformanceTesterDescriptor getDescriptor() {
        return (PerformanceTesterDescriptor) Jenkins.getInstance().getDescriptor(
                this.getClass());
    }
}
