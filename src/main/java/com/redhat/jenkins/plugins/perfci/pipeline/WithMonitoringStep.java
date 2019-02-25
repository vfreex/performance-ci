package com.redhat.jenkins.plugins.perfci.pipeline;

import com.google.common.collect.Sets;
import com.redhat.jenkins.plugins.perfci.common.Listenable;
import com.redhat.jenkins.plugins.perfci.monitors.NMONMonitor;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

public class WithMonitoringStep extends Step implements Serializable {
    public static final String FUNCTION_NAME = "withMonitoring";

    @DataBoundSetter
    private List<NMONMonitor> monitors;

    @DataBoundConstructor
    public WithMonitoringStep(List<NMONMonitor> monitors) {
        this.monitors = monitors;
    }

    public List<NMONMonitor> getMonitors() {
        return monitors;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    private static class Execution extends StepExecution implements Serializable {

        private static final long serialVersionUID = 1L;

        private WithMonitoringStep step;

        private transient TaskListener listener;

        private transient Run<?, ?> run;

        private EnvVars envVars;

        private FilePath path;

        private transient Launcher launcher;


        protected Execution(@Nonnull WithMonitoringStep step, @Nonnull StepContext context) throws IOException, InterruptedException {
            super(context);
            this.step = step;
            this.listener = getContext().get(TaskListener.class);
            this.run = getContext().get(Run.class);
            this.envVars = getContext().get(EnvVars.class);
            this.path = getContext().get(FilePath.class);
            this.launcher = getContext().get(Launcher.class);
        }

        private void startMonitors() throws IOException, InterruptedException {
            listener.getLogger().println("Starting monitors");
            for (NMONMonitor monitor : step.monitors) {
                listener.getLogger().println("Starting monitor for " + monitor);
                if (monitor instanceof Listenable) {
                    Listenable listenable = (Listenable) monitor;
                    listenable.setListener(listener);
                }
                monitor.setWorkDir("/tmp/jenkins-perfci/monitoring/nmon/" + envVars.get("BUILD_TAG"));
                monitor.start(path);
            }
            listener.getLogger().println("Monitors started.");
        }

        private void stopMonitors() throws IOException, InterruptedException {
            boolean allSuccess = true;
            listener.getLogger().println("Stopping monitoring");
            for (NMONMonitor monitor : step.monitors) {
                if (!monitor.isStarted())
                    continue;
                listener.getLogger().println("Stopping monitor for " + monitor);
                try {
                    monitor.stop(path);
                    String resultFile = monitor.collectResult(path, false).getRemote();
                    listener.getLogger().println("Monitoring result is saved to " + resultFile);
                } catch (Exception ex) {
                    listener.getLogger().println("Something went wrong when stopping " + monitor);
                    ex.printStackTrace();
                    allSuccess = false;
                }
            }
            if (!allSuccess) {
                throw new IOException("Couldn't stop some monitors.");
            }
        }

        @Override
        public boolean start() throws IOException, InterruptedException {
            startMonitors();
            getContext().newBodyInvoker().withContext(step).withCallback(new BodyExecutionCallback.TailCall() {
                @Override
                protected void finished(StepContext context) throws Exception {
                    listener.getLogger().println("body ended.");
                    step.monitors.forEach(m -> listener.getLogger().println("isstarted=" + m.isStarted()));
                    stopMonitors();
                }
            }).start();
            listener.getLogger().println("end of start()");
            return false;
        }

        @Override
        public void stop(@Nonnull Throwable cause) throws IOException, InterruptedException {
            listener.getLogger().println("withMonitoring step was interrupted. Aborting started monitors...");
            stopMonitors();
            getContext().onFailure(cause);
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Sets.newHashSet(TaskListener.class, Run.class, EnvVars.class, FilePath.class, Launcher.class);
        }

        @Override
        public String getFunctionName() {
            return FUNCTION_NAME;
        }

        @Override
        public String getDisplayName() {
            return "Monitor hardware resources when invoking the closure";
        }

        @Override
        public boolean isAdvanced() {
            return false;
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }
}
