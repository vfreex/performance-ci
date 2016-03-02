package org.jenkinsci.plugins.perfci.builder;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.perfci.action.PerfchartsBuildReportAction;
import org.jenkinsci.plugins.perfci.action.PerfchartsTrendReportAction;
import org.jenkinsci.plugins.perfci.common.*;
import org.jenkinsci.plugins.perfci.common.TaskQueue;
import org.jenkinsci.plugins.perfci.executor.PerfchartsNewExecutor;
import org.jenkinsci.plugins.perfci.model.PerformanceTester;
import org.jenkinsci.plugins.perfci.model.ResourceMonitor;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

/**
 * Created by vfreex on 11/23/15.
 */
public class PerformanceTestBuilder extends Builder implements Serializable {
    private boolean disabled;
    private String resultDir;
    private int keepBuilds;
    private boolean reportDisabled;
    private String fallbackTimezone;
    private List<PerformanceTester> performanceTesters;
    private List<ResourceMonitor> resourceMonitors;
    private String perfchartsCommand;
    private String excludedTransactionPattern;
    private String reportTemplate;

    @DataBoundConstructor
    public PerformanceTestBuilder(boolean disabled, String resultDir, int keepBuilds, boolean reportDisabled, String fallbackTimezone, List<PerformanceTester> performanceTesters, List<ResourceMonitor> resourceMonitors, String perfchartsCommand, String excludedTransactionPattern, String reportTemplate) {
        this.disabled = disabled;
        this.resultDir = resultDir;
        this.keepBuilds = keepBuilds;
        this.reportDisabled = reportDisabled;
        this.fallbackTimezone = fallbackTimezone;
        this.perfchartsCommand = perfchartsCommand;
        this.excludedTransactionPattern = excludedTransactionPattern;
        this.reportTemplate = reportTemplate;
        this.performanceTesters = performanceTesters != null ? performanceTesters : Collections.<PerformanceTester>emptyList();
        this.resourceMonitors = resourceMonitors != null ? resourceMonitors : Collections.<ResourceMonitor>emptyList();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws InterruptedException, IOException {
        if (disabled) {
            listener.getLogger().println("[WARNING] Ignore PerformanceTestBuilder");
            return true;
        }
        final TimeZone fallbackTimezoneObj = TimeZone.getTimeZone(fallbackTimezone);
        // `buildDir` here is the directory where we put all test results and logs for this build. It is a relative path to Jenkins workspace.
        final String resultDir = this.resultDir == null ? "" : this.resultDir;
        final String buildDir = resultDir + File.separator + "builds" + File.separator + +build.number;
        final String baseDirForBuild = buildDir + File.separator + "rawdata";
        final String logDirForBuild = buildDir + File.separator + "log";
        final String reportDirForBuild = buildDir + File.separator + "report";
        EnvVars env = build.getEnvironment(listener);
        final String perfchartsCommand = env.expand(this.perfchartsCommand);

        // start resource monitors
        TaskQueue startMonitorTaskQueue = new TaskQueue();
        for (final ResourceMonitor resourceMonitor : resourceMonitors) {
            startMonitorTaskQueue.enqueue(new Runnable() {
                @Override
                public void run() {
                    if (resourceMonitor instanceof BaseDirectoryRelocatable) {
                        ((BaseDirectoryRelocatable) resourceMonitor).setBaseDirectory(baseDirForBuild);
                    }
                    if (resourceMonitor instanceof ResultDirectoryRelocatable) {
                        ((ResultDirectoryRelocatable) resourceMonitor).setResultDirectory(resultDir);
                    }
                    try {
                        resourceMonitor.start(build, launcher, listener);
                    } catch (Exception ex) {
                        Thread t = Thread.currentThread();
                        t.getUncaughtExceptionHandler().uncaughtException(t, ex);
                    }
                }
            });
        }
        listener.getLogger().println("INFO: Wait at most 10 minutes for resource monitors to start");
        startMonitorTaskQueue.runAll(1000 * 60 * 10);
        // start performance test executors
        for (PerformanceTester performanceTester : performanceTesters) {
            if (performanceTester instanceof LogDirectoryRelocatable) {
                ((LogDirectoryRelocatable) performanceTester).setLogDirectory(logDirForBuild);
            }
            if (performanceTester instanceof BaseDirectoryRelocatable) {
                ((BaseDirectoryRelocatable) performanceTester).setBaseDirectory(baseDirForBuild);
            }
            if (performanceTester instanceof ResultDirectoryRelocatable) {
                ((ResultDirectoryRelocatable) performanceTester).setResultDirectory(resultDir);
            }
            performanceTester.run(build, launcher, listener);
        }

        // stop resource monitors and collect test results
        TaskQueue stopMonitorTaskQueue = new TaskQueue();
        for (final ResourceMonitor resourceMonitor : resourceMonitors) {
            stopMonitorTaskQueue.enqueue(new Runnable() {
                @Override
                public void run() {
                    if (resourceMonitor instanceof BaseDirectoryRelocatable) {
                        ((BaseDirectoryRelocatable) resourceMonitor).setBaseDirectory(baseDirForBuild);
                    }
                    try {
                        resourceMonitor.stop(build, launcher, listener);
                    } catch (Exception ex) {
                        Thread t = Thread.currentThread();
                        t.getUncaughtExceptionHandler().uncaughtException(t, ex);
                    }
                }
            });
        }
        listener.getLogger().println("INFO: Waiting at most 6 hours for resource monitors to stop and complete data transfer...");
        stopMonitorTaskQueue.runAll(1000 * 60 * 60 * 6);

        if (reportDisabled) {
            listener.getLogger().println("WARNING: No performance test reports will be generated according to your configuration.");
        } else {
            final String workspaceFullPathOnAgent = build.getWorkspace().getRemote();
            launcher.getChannel().call(new hudson.remoting.Callable<Object, IOException>() {
                @Override
                public void checkRoles(RoleChecker checker) throws SecurityException {
                }

                @Override
                public Object call() throws IOException {
                    // generate a report
                    PerfchartsNewExecutor perfchartsExecutor = new PerfchartsNewExecutor(perfchartsCommand,
                            reportTemplate, workspaceFullPathOnAgent,
                            fallbackTimezoneObj,
                            baseDirForBuild,
                            reportDirForBuild,
                            reportDirForBuild + File.separator + Constants.MONO_REPORT_NAME,
                            PerformanceTestBuilder.this.excludedTransactionPattern,
                            listener.getLogger());


                    try {
                        if (perfchartsExecutor.run() != 0) {
                            listener.getLogger().println("ERROR: Perfcharts reported an error when generating a performance report.");
                            throw new InterruptedException("Perfcharts reported an error when generating a performance report.");
                        }
                        listener.getLogger().println("INFO: Performance report generated successfully.");
                    } catch (InterruptedException e) {
                        throw new IOException(e);
                    }
                    return null;
                }
            });
            // copy generated report to master
            listener.getLogger().println("INFO: Copying generated performance report to Jenkins master...");
            IOHelper.copyDirFromWorkspace(build.getWorkspace().child(reportDirForBuild), Constants.PERF_CHARTS_RELATIVE_PATH, build, listener);
        }

        listener.getLogger().println("INFO: Preparing views for generated performance report...");
        build.addAction(new PerfchartsBuildReportAction(build));

        listener.getLogger().println("INFO: Everything is done.");
        return true;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        List<Action> actions = new ArrayList<Action>();
        actions.add(new PerfchartsTrendReportAction(project));
        return actions;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public String getFallbackTimezone() {
        return fallbackTimezone;
    }

    public void setFallbackTimezone(String fallbackTimezone) {
        this.fallbackTimezone = fallbackTimezone;
    }

    public boolean isReportDisabled() {
        return reportDisabled;
    }

    public void setReportDisabled(boolean reportDisabled) {
        this.reportDisabled = reportDisabled;
    }

    public String getResultDir() {
        return resultDir;
    }

    public void setResultDir(String resultDir) {
        this.resultDir = resultDir;
    }

    public List<PerformanceTester> getPerformanceTesters() {
        return performanceTesters;
    }

    public void setPerformanceTesters(List<PerformanceTester> performanceTesters) {
        this.performanceTesters = performanceTesters;
    }

    public int getKeepBuilds() {
        return keepBuilds;
    }

    public void setKeepBuilds(int keepBuilds) {
        this.keepBuilds = keepBuilds;
    }

    public List<ResourceMonitor> getResourceMonitors() {
        return resourceMonitors;
    }

    public void setResourceMonitors(List<ResourceMonitor> resourceMonitors) {
        this.resourceMonitors = resourceMonitors;
    }

    public String getPerfchartsCommand() {
        return perfchartsCommand;
    }

    public void setPerfchartsCommand(String perfchartsCommand) {
        this.perfchartsCommand = perfchartsCommand;
    }

    public String getExcludedTransactionPattern() {
        return excludedTransactionPattern;
    }

    public void setExcludedTransactionPattern(String excludedTransactionPattern) {
        this.excludedTransactionPattern = excludedTransactionPattern;
    }

    public String getReportTemplate() {
        return reportTemplate;
    }

    public void setReportTemplate(String reportTemplate) {
        this.reportTemplate = reportTemplate;
    }

    /**
     * Descriptor for {@link PerformanceTestBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public DescriptorImpl() {
            load();
        }


        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Run performance test";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

        public List<? extends PerformanceTester.PerformanceTesterDescriptor> getPerformanceTesterDescriptors() {
            return Jenkins
                    .getInstance()
                    .<PerformanceTester, PerformanceTester.PerformanceTesterDescriptor>getDescriptorList(
                            PerformanceTester.class);
        }

        public List<? extends ResourceMonitor.ResourceMonitorDescriptor> getResourceMonitorDescriptors() {
            return Jenkins
                    .getInstance()
                    .<ResourceMonitor, ResourceMonitor.ResourceMonitorDescriptor>getDescriptorList(
                            ResourceMonitor.class);
        }

        public ListBoxModel doFillReportTemplateItems() {
            return new ListBoxModel(new ListBoxModel.Option("Performance baseline test", "perf-baseline"),
                    new ListBoxModel.Option("General purpose performance test", "perf-general"));
        }
    }


}
