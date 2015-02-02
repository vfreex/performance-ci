package org.jenkinsci.plugins.perfci.monitoring;

import com.fasterxml.jackson.databind.ObjectMapper;

import hudson.Launcher;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.model.ParametersAction;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;

import java.io.File;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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
	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) {
		// This is where you 'build' the project.
		// Since this is a dummy, we just say 'hello world' and call that a
		// build.

		// This also shows how you can consult the global configuration of the
		// builder
		// if (getDescriptor().getUseFrench()) {
		// listener.getLogger().println("Hello, stop monitoring !");
		// } else {
		// listener.getLogger().println("Hello, stop monitoring !");
		// }

		ParametersAction paraAction = build.getAction(ParametersAction.class);
		ResourceMonitor.ResourceMonitorParameterValue para = (ResourceMonitor.ResourceMonitorParameterValue)paraAction.getParameter("monitors");
		List<ResourceMonitor> monitors = para.getMonitors();
		for (ResourceMonitor monitor : monitors) {
			try {
				LOGGER.info("Stopping monitor '" + monitor.getClass().getName() + "'...");
				monitor.stop(build, launcher, listener);
				LOGGER.info("Monitor '" + monitor.getClass().getName() + "' stopped.");
			} catch (Exception e) {
				e.printStackTrace();
				LOGGER.info("Opps! Something went wrong when trying stopping monitor '" + monitor.getClass().getName() + "'!");
				return false;
			}
		}
		
		//		// MapParameterValue mapParam1 = (MapParameterValue)
//		// actions.getParameter(ResourceMonitor.ParameterName);
//
//		try {
//			File rootpath = build.getRootDir();
//			File monitoringDir = new File(rootpath.getAbsoluteFile()
//					+ "/performance-reports/monitoring");
//			if (!monitoringDir.exists()) {
//				monitoringDir.mkdirs();
//			}
//			Map allAvgDatas = new HashMap();
//
//			List<ResourceMonitor> monitors = (List<ResourceMonitor>) mapParam1
//					.getData().get("Monitors");
//			for (ResourceMonitor resourceMonitor : monitors) {
//				allAvgDatas.putAll(resourceMonitor.stopMonitoring(build,
//						launcher, listener));
//			}
//
//			// mapParam1.clear();
//			// mapParam1 = null;
//
//			// ObjectMapper mapper = new ObjectMapper();
//			// mapper.writeValue(new File(monitoringDir.getAbsolutePath()
//			// +"/resources.json"), allAvgDatas);
//		} catch (IOException ex) {
//			LOGGER.log(Level.SEVERE,
//					null, ex);
//		}
		LOGGER.info("All monitors stopped.");
		return true;
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

		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 *
		 * @param value
		 *            This parameter receives the value that the user has typed.
		 * @return Indicates the outcome of the validation. This is sent to the
		 *         browser.
		 */
		public FormValidation doCheckName(@QueryParameter String value)
				throws IOException, ServletException {
			if (value.length() == 0) {
				return FormValidation.error("Please set a name");
			}
			if (value.length() < 4) {
				return FormValidation.warning("Isn't the name too short?");
			}
			return FormValidation.ok();
		}

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
}
