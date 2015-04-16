package org.jenkinsci.plugins.perfci.monitoring;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.ParametersAction;
import hudson.remoting.Callable;
import hudson.remoting.RemoteOutputStream;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import jenkins.model.Jenkins;
import jenkins.security.Roles;

import org.jenkinsci.remoting.RoleChecker;
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
		try {
			Boolean r = launcher.getChannel().call(
					new StartMonitorsCallable(build.getProject().getName(),
							build.getId(), build.getWorkspace().getRemote(),
							monitors, listener));
			if (r != null && r.booleanValue()) {
				build.addAction(new ParametersAction(
						new ResourceMonitor.ResourceMonitorParameterValue(
								"monitors", monitors)));
				LOGGER.info("All monitors started.");
				listener.getLogger().println("INFO: All monitors started.");
				return true;
			}
			return false;
		} catch (Exception e) {
			RuntimeException re = new RuntimeException();
			re.initCause(e);
			throw re;
		}
	}

	public List<ResourceMonitor> getMonitors() {
		return monitors;
	}

	public void setMonitors(List<ResourceMonitor> monitors) {
		this.monitors = monitors;
	}

	private static class StartMonitorsCallable implements
			Callable<Boolean, Exception> {
		private static final long serialVersionUID = 1L;
		private String projectName;
		private String buildID;
		private String workspace;
		private List<ResourceMonitor> monitors;
		private BuildListener listener;

		public StartMonitorsCallable(String projectName, String buildID,
				String workspace, List<ResourceMonitor> monitors,
				BuildListener listener) {
			this.projectName = projectName;
			this.buildID = buildID;
			this.workspace = workspace;
			this.monitors = monitors;
			this.listener = listener;
		}

		@Override
		public void checkRoles(RoleChecker checker) throws SecurityException {
			checker.check(this, Roles.SLAVE, Roles.MASTER);
		}

		@Override
		public Boolean call() throws Exception {
			final RemoteOutputStream ros = new RemoteOutputStream(
					listener.getLogger());
			final PrintStream logWritter = new PrintStream(ros);
			try {
				final AtomicInteger errorCount = new AtomicInteger(0);
				int threadCount = Math.min(monitors.size(), Runtime
						.getRuntime().availableProcessors() * 2);
				Thread[] threads = new Thread[threadCount];
				final AtomicInteger remainedTasks = new AtomicInteger(
						monitors.size());
				for (int t = 0; t < threads.length; t++) {
					threads[t] = new Thread(new Runnable() {
						@Override
						public void run() {
							int tid;
							while ((tid = remainedTasks.decrementAndGet()) >= 0) {
								ResourceMonitor monitor = monitors.get(tid);
								try {
									LOGGER.info("Start monitor '"
											+ monitor.getClass().getName()
											+ "'...");
									logWritter.println("INFO: Start monitor '"
											+ monitor.getClass().getName()
											+ "'...");
									if (!monitor.start(projectName, buildID,
											workspace, logWritter)) {
										errorCount.incrementAndGet();
										break;
									}
									LOGGER.info("Monitor '"
											+ monitor.getClass().getName()
											+ "' started.");
									logWritter.println("INFO: Monitor '"
											+ monitor.getClass().getName()
											+ "' started.");
								} catch (Exception e) {
									errorCount.incrementAndGet();
									e.printStackTrace();
									LOGGER.warning("Opps! Something went wrong when trying starting monitor '"
											+ monitor.getClass().getName()
											+ "'!");
									logWritter
											.println("WARNING: Opps! Something went wrong when trying starting monitor '"
													+ monitor.getClass()
															.getName() + "'!");

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
				return true;
			} finally {
				logWritter.flush();
			}
		}

	}
}
