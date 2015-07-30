package org.jenkinsci.plugins.perfci.monitoring;

import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ParameterValue;
import hudson.remoting.Callable;
import org.jenkinsci.remoting.RoleChecker;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.List;

public interface ResourceMonitor extends Describable<ResourceMonitor>, Serializable {
    boolean start(String projectName, String buildID, String workspace, PrintStream listener)
            throws Exception;

    boolean stop(String projectName, String buildID, String workspace, PrintStream listener)
            throws Exception;

    boolean isEnabled();

    void checkRoles(RoleChecker checker, Callable<?, ? extends SecurityException> callable) throws SecurityException;

    abstract class ResourceMonitorDescriptor extends
            Descriptor<ResourceMonitor> {

    }

    class ResourceMonitorParameterValue extends ParameterValue {
        /**
         *
         */
        private static final long serialVersionUID = -970277688612962704L;
        private List<ResourceMonitor> monitors;

        protected ResourceMonitorParameterValue(String name) {
            super(name);
        }

        protected ResourceMonitorParameterValue(String name,
                                                List<ResourceMonitor> monitors) {
            super(name);
            this.setMonitors(monitors);
        }

        public List<ResourceMonitor> getMonitors() {
            return monitors;
        }

        public void setMonitors(List<ResourceMonitor> monitors) {
            this.monitors = monitors;
        }
    }
}
