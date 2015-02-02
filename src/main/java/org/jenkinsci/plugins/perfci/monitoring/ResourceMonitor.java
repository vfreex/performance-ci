package org.jenkinsci.plugins.perfci.monitoring;

import java.util.List;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.ParameterValue;
import hudson.model.Descriptor;

public interface ResourceMonitor extends Describable<ResourceMonitor> {
	boolean start(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws Exception;

	boolean stop(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws Exception;

	void collect(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener);

	boolean isRuning(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener);

	abstract static class ResourceMonitorDescriptor extends
			Descriptor<ResourceMonitor> {

	}
	public static class ResourceMonitorParameterValue extends ParameterValue {
		private List<ResourceMonitor> monitors;
		protected ResourceMonitorParameterValue(String name) {
			super(name);
		}
		protected ResourceMonitorParameterValue(String name, List<ResourceMonitor> monitors) {
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
