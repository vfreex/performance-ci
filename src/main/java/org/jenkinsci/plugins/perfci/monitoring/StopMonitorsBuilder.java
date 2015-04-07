package org.jenkinsci.plugins.perfci.monitoring;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ParametersAction;
import hudson.model.Project;
import hudson.remoting.Callable;
import hudson.remoting.RemoteOutputStream;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import jenkins.MasterToSlaveFileCallable;
import jenkins.SlaveToMasterFileCallable;
import jenkins.model.Jenkins;
import jenkins.security.Roles;
import net.sf.json.JSONObject;

import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

public class StopMonitorsBuilder extends Builder {

	private final static Logger LOGGER = Logger
			.getLogger(StopMonitorsBuilder.class.getName());

	@DataBoundConstructor
	public StopMonitorsBuilder() {

	}

	/**
	 * We'll use this from the <tt>config.jelly</tt>.
	 */
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) {
		ParametersAction paraAction = build.getAction(ParametersAction.class);
		if (paraAction == null) {
			listener.getLogger().println("ERROR: No monitors to stop.");
			return false;
		}
		ResourceMonitor.ResourceMonitorParameterValue para = (ResourceMonitor.ResourceMonitorParameterValue) paraAction
				.getParameter("monitors");
		if (para == null) {
			listener.getLogger().println("ERROR: No monitors to stop.");
			return false;
		}
		List<ResourceMonitor> monitors = para.getMonitors();
		try {
			Boolean r = launcher.getChannel().call(
					new StopMonitorsCallable(build.getProject().getName(),
							build.getId(), build.getWorkspace().getRemote(),
							monitors, listener));
			return r != null && r.booleanValue();
		} catch (Exception e) {
			RuntimeException re = new RuntimeException();
			re.initCause(e);
			throw re;
		}

	}

	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Descriptor for {@link HelloWorldBuilder}. Used as a singleton. The class
	 * is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See
	 * <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension
	// This indicates to Jenkins that this is an implementation of an extension
	// point.
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Builder> {

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "Stop Resource Monitors";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData)
				throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().

			// ^Can also use req.bindJSON(this, formData);
			// (easier when there are many fields; need set* methods for this,
			// like setUseFrench)
			save();
			return super.configure(req, formData);
		}

	}

	private static class StopMonitorsCallable implements
			Callable<Boolean, IOException> {
		private static final long serialVersionUID = 1L;
		private String projectName;
		private String buildID;
		private String workspace;
		private List<ResourceMonitor> monitors;
		private BuildListener listener;

		public StopMonitorsCallable(String projectName, String buildID,
				String workspace, List<ResourceMonitor> monitors,
				BuildListener listener) {
			super();
			this.projectName = projectName;
			this.buildID = buildID;
			this.monitors = monitors;
			this.listener = listener;
			this.workspace = workspace;
		}

		@Override
		public Boolean call() throws IOException {
			final RemoteOutputStream ros = new RemoteOutputStream(
					listener.getLogger());
			final PrintStream logWritter = new PrintStream(ros);
			try {
				int stoppedMonitors = 0;
				for (ResourceMonitor monitor : monitors) {
					try {
						LOGGER.info("Stopping monitor '"
								+ monitor.getClass().getName() + "'...");
						logWritter.println("INFO: Stopping monitor '"
								+ monitor.getClass().getName() + "'...");
						if (!monitor.stop(projectName, buildID, workspace,
								listener)) {
							LOGGER.warning("Something goes wrong when trying stopping Monitor '"
									+ monitor.getClass().getName() + "'.");
							logWritter
									.println("WARNING: Something goes wrong when trying stopping Monitor '"
											+ monitor.getClass().getName()
											+ "'.");
							continue;
						}
						++stoppedMonitors;
						LOGGER.info("Monitor '" + monitor.getClass().getName()
								+ "' stopped.");
						logWritter.println("INFO: Monitor '"
								+ monitor.getClass().getName() + "' stopped.");
					} catch (Exception e) {
						e.printStackTrace();
						LOGGER.warning("Opps! Something went wrong when trying stopping monitor '"
								+ monitor.getClass().getName() + "'!");
						logWritter
								.println("WARNING: Opps! Something went wrong when trying stopping monitor '"
										+ monitor.getClass().getName() + "'!");
						return false;
					}
				}
				if (stoppedMonitors == monitors.size()) {
					LOGGER.info("All monitors stopped.");
					logWritter.println("INFO: All monitors stopped.");
					return true;
				} else {
					LOGGER.warning("Opps! Something went wrong when trying stopping monitors! Mark this build failure.");
					logWritter
							.println("WARNING: Opps! Something went wrong when trying stopping monitors! Mark this build failure.");
					return false;
				}
			} finally {
				logWritter.close();
			}
		}

		@Override
		public void checkRoles(RoleChecker checker) throws SecurityException {
			checker.check(this, Roles.SLAVE, Roles.MASTER);
		}

	}
}
