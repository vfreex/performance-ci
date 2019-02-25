package com.redhat.jenkins.plugins.perfci.monitors;

import com.google.common.collect.Lists;
import com.redhat.jenkins.plugins.perfci.common.Listenable;
import com.redhat.jenkins.plugins.perfci.targets.MonitoringTarget;
import com.redhat.jenkins.plugins.perfci.targets.RunCommandResult;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import org.jenkinsci.Symbol;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NMONMonitor extends AbstractDescribableImpl<NMONMonitor> implements Listenable, Serializable, AutoCloseable {
    private MonitoringTarget target;
    @DataBoundSetter
    private String executable = "nmon";

    @DataBoundSetter
    private List<String> options;
    @DataBoundSetter
    private int interval = 1;
    @DataBoundSetter
    private int snapshots = 60 * 60 * 24 * 3;
    @DataBoundSetter
    private Map<String, String> envVars = Collections.emptyMap();
    @DataBoundSetter
    private String dir;
    @DataBoundSetter
    private String fileName;

    private String workDir;

    private transient TaskListener listener;
    private int pid;

    @DataBoundConstructor
    public NMONMonitor(@Nonnull MonitoringTarget target) {
        this.target = target;
    }

    public void start(@Nonnull FilePath path) throws InterruptedException, IOException {
        if (this.pid != 0) {
            throw new IOException("NMON already started.");
        }
        List<String> actualOptions = Lists.newArrayList("-f", "-p", "-t");
        if (interval > 0) {
            actualOptions.add("-s");
            actualOptions.add(Integer.toString(interval));
        }
        if (snapshots > 0) {
            actualOptions.add("-c");
            actualOptions.add(Integer.toString(snapshots));
        }
        if (workDir != null && !workDir.isEmpty()) {
            actualOptions.add("-m");
            actualOptions.add(workDir);
        }
        if (fileName != null && fileName.isEmpty()) {
            actualOptions.add("-F");
            actualOptions.add(fileName);
        }
        if (options != null) {
            actualOptions.addAll(options);
        }
        final TaskListener listener = this.listener;
        final MonitoringTarget target = this.target;
        try {
            RunCommandResult result;
            if (workDir != null && !workDir.isEmpty()) {
                printConsole(String.format("Creating NMON working directory %s on target...", workDir));
                result = path.act(new Callable<RunCommandResult, Exception>() {
                    @Override
                    public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                    }

                    @Override
                    public RunCommandResult call() throws Exception {
                        try {
                            target.setListener(listener);
                            target.connect();
                            return target.run("mkdir", Lists.newArrayList("-p", "--", workDir), null);
                        } finally {
                            target.close();
                        }
                    }
                });
                if (result.getStatus() != 0) {
                    throw new IOException("Failed to create NMON working directory: " + result);
                }
            }
            printConsole("Starting NMON process...");
            result = path.act(new Callable<RunCommandResult, Exception>() {
                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                }

                @Override
                public RunCommandResult call() throws Exception {
                    try {
                        target.setListener(listener);
                        target.connect();
                        return target.run(executable, actualOptions, envVars);
                    } finally {
                        target.close();
                    }
                }
            });
            if (result.getStatus() != 0) {
                throw new IOException("Failed to start NMON:" + result);
            }
            this.pid = Integer.parseInt(result.getOut().trim());
            printConsole("NMON started as PID " + this.pid);
        } catch (Exception e) {
            if (e instanceof IOException)
                throw (IOException) e;
            else if (e instanceof InterruptedException)
                throw (InterruptedException) e;
            else
                new IOException(e);
        }

    }

    public boolean isStarted() {
        return this.pid != 0;
    }

    public boolean isRunning(FilePath path) throws IOException, InterruptedException {
        if (this.pid == 0)
            return false;
        final TaskListener listener = this.listener;
        final MonitoringTarget target = this.target;
        final int pid = this.pid;
        final String executable = this.executable;
        try {
            return path.act(new Callable<Boolean, Exception>() {
                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                }

                @Override
                public Boolean call() throws Exception {
                    target.setListener(listener);
                    try {
                        target.connect();
                        RunCommandResult result = target.run("pgrep", Lists.newArrayList("--", executable), null);
                        if (result.getStatus() != 0) {
                            throw new IOException("Failed to list running NMON processes:" + result);
                        }
                        return Arrays.stream(result.getOut().split("\n")).map(str -> Integer.parseInt(str)).anyMatch(it -> it == pid);
                    } finally {
                        target.close();
                    }
                }
            });
        } catch (Exception e) {
            if (e instanceof IOException)
                throw (IOException) e;
            else if (e instanceof InterruptedException)
                throw (InterruptedException) e;
            else
                throw new IOException(e);
        }
    }

    public void stop(@Nonnull FilePath path) throws InterruptedException, IOException {
        if (this.pid == 0) {
            printConsole("Already stopped.");
            return;
        }
        final TaskListener listener = this.listener;
        final MonitoringTarget target = this.target;
        final int pid = this.pid;
        final String executable = this.executable;
        try {
            path.act(new Callable<Void, Exception>() {
                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                }

                @Override
                public Void call() throws Exception {
                    target.setListener(listener);
                    try {
                        target.connect();
                        RunCommandResult result = target.run("pgrep", Lists.newArrayList("--", executable), null);
                        if (result.getStatus() != 0) {
                            throw new IOException("Failed to list running NMON processes:" + result);
                        }
                        boolean running = Arrays.stream(result.getOut().split("\n")).map(str -> Integer.parseInt(str)).anyMatch(it -> it == pid);
                        if (!running) {
                            printConsole(String.format("NMON with PID %s is dead.", pid));
                            return null;
                        }
                        result = target.run("kill", Lists.newArrayList("-s", "TERM", "--", Integer.toString(pid)), null);
                        if (result.getStatus() != 0) {
                            throw new IOException("Failed to kill NMON:" + result);
                        }
                    } finally {
                        target.close();
                    }
                    return null;
                }
            });
            this.pid = 0;
        } catch (Exception e) {
            if (e instanceof IOException)
                throw (IOException) e;
            else if (e instanceof InterruptedException)
                throw (InterruptedException) e;
            else
                throw new IOException(e);
        }
    }

    public FilePath collectResult(FilePath path, boolean deleteFromTarget) throws IOException, InterruptedException {
        final TaskListener listener = this.listener;
        final MonitoringTarget target = this.target;
        final String fileName = this.fileName;
        final String resultDirOnTarget = workDir == null || workDir.isEmpty() ? "." : workDir;
        final FilePath destDir = dir == null || dir.isEmpty() ? path : path.child(dir);
        try {
            return path.act(new Callable<FilePath, Exception>() {
                @Override
                public void checkRoles(RoleChecker roleChecker) throws SecurityException {
                }

                @Override
                public FilePath call() throws Exception {
                    target.setListener(listener);
                    try {
                        target.connect();
                        String resultFileName = fileName;
                        if (resultFileName == null || resultFileName.isEmpty()) {
                            List<String> remoteFiles = target.ls(resultDirOnTarget);
                            if (remoteFiles.isEmpty()) {
                                throw new IOException("No NMON results found.");
                            }
                            resultFileName = remoteFiles.get(0);
                        }
                        String resultPathOnTarget = resultDirOnTarget + "/" + resultFileName;
                        destDir.mkdirs();
                        target.download(resultPathOnTarget, destDir.getRemote(), deleteFromTarget);
                        return destDir.child(resultFileName);
                    } finally {
                        target.close();
                    }
                }
            });
        } catch (Exception e) {
            if (e instanceof IOException)
                throw (IOException) e;
            else if (e instanceof InterruptedException)
                throw (InterruptedException) e;
            else
                throw new IOException(e);
        }
    }

    public MonitoringTarget getTarget() {
        return target;
    }

    public void setTarget(MonitoringTarget target) {
        this.target = target;
    }

    public String getExecutable() {
        return executable;
    }

    public void setExecutable(String executable) {
        this.executable = executable;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public Map<String, String> getEnvVars() {
        return envVars;
    }

    public void setEnvVars(Map<String, String> envVars) {
        this.envVars = envVars;
    }

    @Override
    public String toString() {
        return String.format("NMON Monitor for target %s", target);
    }

    @Override
    public void setListener(TaskListener listener) {
        this.listener = listener;
        if (target instanceof Listenable)
            ((Listenable) target).setListener(listener);
    }

    @Override
    public TaskListener getListener() {
        return this.listener;
    }

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }

    @Override
    public void close() throws IOException, InterruptedException {
        if (this.target.isReady()) {
            this.target.disconnect();
        }
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public int getSnapshots() {
        return snapshots;
    }

    public void setSnapshots(int snapshots) {
        this.snapshots = snapshots;
    }

    @Extension
    @Symbol("nmon")
    public static class DescriptorImpl extends Descriptor<NMONMonitor> {
        @Override
        public String getDisplayName() {
            return "NMON monitor";
        }
    }
}
