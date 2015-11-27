package org.jenkinsci.plugins.perfci.model;

import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.remoting.Callable;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.perfci.common.BaseDirectoryRelocatable;
import org.jenkinsci.remoting.RoleChecker;

import java.io.IOException;
import java.io.Serializable;

public abstract class ResourceMonitor implements Describable<ResourceMonitor>, Serializable, ExtensionPoint {

    public abstract boolean start(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;

    public abstract boolean stop(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException;

    public abstract boolean isEnabled();

    public abstract void checkRoles(RoleChecker checker, Callable<?, ? extends SecurityException> callable) throws SecurityException;

    public abstract static class ResourceMonitorDescriptor extends
            Descriptor<ResourceMonitor> {
    }

    @Override
    public Descriptor<ResourceMonitor> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(
                this.getClass());
    }

}
