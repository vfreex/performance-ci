package org.jenkinsci.plugins.perfci.monitoring;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.ParametersAction;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

public class StartMonitorsBuilder extends Builder {
	private static final Logger LOGGER = Logger
			.getLogger(StartMonitorsBuilder.class.getName());

	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Builder> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return "Start Resource Monitors";
		}

		public List<? extends ResourceMonitor.ResourceMonitorDescriptor> getMonitorDescriptors() {
			return Jenkins
					.getInstance()
					.<ResourceMonitor, ResourceMonitor.ResourceMonitorDescriptor> getDescriptorList(
							ResourceMonitor.class);
		}

	}

	private List<ResourceMonitor> monitors;

	@DataBoundConstructor
	public StartMonitorsBuilder(List<ResourceMonitor> monitors) {
		this.monitors = monitors != null ? monitors : Collections
				.<ResourceMonitor> emptyList();
	}

	@Override
	public Descriptor<Builder> getDescriptor() {
		return super.getDescriptor();
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build,
			final Launcher launcher, final BuildListener listener)
			throws InterruptedException, IOException {
		final AtomicInteger errorCount = new AtomicInteger(0);
		int threadCount = Math.min(monitors.size(), Runtime.getRuntime()
				.availableProcessors() * 2);
		Thread[] threads = new Thread[threadCount];
		final AtomicInteger remainedTasks = new AtomicInteger(monitors.size());
		for (int t = 0; t < threads.length; t++) {
			threads[t] = new Thread(new Runnable() {
				@Override
				public void run() {
					int tid;
					while ((tid = remainedTasks.decrementAndGet()) >= 0) {
						ResourceMonitor monitor = monitors.get(tid);
						try {
							LOGGER.info("Start monitor '"
									+ monitor.getClass().getName() + "'...");
							listener.getLogger().println(
									"INFO: Start monitor '"
											+ monitor.getClass().getName()
											+ "'...");
							if (!monitor.start(build, launcher, listener)) {
								errorCount.incrementAndGet();
								break;
							}
							LOGGER.info("Monitor '"
									+ monitor.getClass().getName()
									+ "' started.");
							listener.getLogger().println(
									"INFO: Monitor '"
											+ monitor.getClass().getName()
											+ "' started.");
						} catch (Exception e) {
							errorCount.incrementAndGet();
							e.printStackTrace();
							LOGGER.warning("Opps! Something went wrong when trying starting monitor '"
									+ monitor.getClass().getName() + "'!");
							listener.getLogger().println(
									"WARNING: Opps! Something went wrong when trying starting monitor '"
											+ monitor.getClass().getName()
											+ "'!");

							break;
						}
					}
				}
			});
		}
		for (int t = 0; t < threads.length; t++) {
			threads[t].start();
		}
		for (int t = 0; t < threads.length; t++) {
			threads[t].join();
		}
		if (errorCount.get() > 0)
			return false;
		build.addAction(new ParametersAction(
				new ResourceMonitor.ResourceMonitorParameterValue("monitors",
						monitors)));
		LOGGER.info("All monitors started.");
		listener.getLogger().println("INFO: All monitors started.");
		return true;
	}

	public List<ResourceMonitor> getMonitors() {
		return monitors;
	}

	public void setMonitors(List<ResourceMonitor> monitors) {
		this.monitors = monitors;
	}

}
