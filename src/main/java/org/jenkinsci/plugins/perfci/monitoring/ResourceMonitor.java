package org.jenkinsci.plugins.perfci.monitoring;

import java.io.PrintStream;
import java.io.Serializable;
import java.util.List;

import org.jenkinsci.remoting.RoleChecker;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.ParameterValue;
import hudson.model.Descriptor;
import hudson.remoting.Callable;

public interface ResourceMonitor extends Describable<ResourceMonitor>, Serializable {
	boolean start(String projectName, String buildID, String workspace, PrintStream listener)
			throws Exception;

	boolean stop(String projectName, String buildID, String workspace, PrintStream listener)
			throws Exception;
	
	void checkRoles(RoleChecker checker, Callable<?, ? extends SecurityException> callable) throws SecurityException;

	abstract static class ResourceMonitorDescriptor extends
			Descriptor<ResourceMonitor> {

	}

	public static class ResourceMonitorParameterValue extends ParameterValue {
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
