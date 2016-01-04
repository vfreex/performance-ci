package org.jenkinsci.plugins.perfci.model;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.remoting.Callable;
import hudson.util.FormValidation;
import org.apache.tools.ant.types.Commandline;
import org.jenkinsci.plugins.perfci.common.BaseDirectoryRelocatable;
import org.jenkinsci.plugins.perfci.common.LogDirectoryRelocatable;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.springframework.util.AntPathMatcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by vfreex on 11/23/15.
 */
public class JmeterPerformanceTester extends PerformanceTester implements LogDirectoryRelocatable, BaseDirectoryRelocatable {
    @Extension
    public static class DescriptorImpl extends PerformanceTester.PerformanceTesterDescriptor {
        @Override
        public String getDisplayName() {
            return "Apache Jmeter";
        }

        public FormValidation doCheckJmxIncludingPattern(@QueryParameter String jmxIncludingPattern) {
            if (jmxIncludingPattern == null || jmxIncludingPattern.isEmpty()) {
                return FormValidation.error("This blank is required. Maybe you can simply use `**/*.jmx`.");
            }
//            AntPathMatcher antPathMatcher = new AntPathMatcher();
//            if (!antPathMatcher.isPattern(jmxIncludingPattern)){
//                return FormValidation.error("Invalid pattern format.");
//            }
            return FormValidation.ok();
        }
        public FormValidation doCheckJmxExcludingPattern(@QueryParameter String jmxExcludingPattern) {
//            if (jmxExcludingPattern == null || jmxExcludingPattern.isEmpty()) {
//                return FormValidation.ok();
//            }
//            AntPathMatcher antPathMatcher = new AntPathMatcher();
//            if (!antPathMatcher..isPattern(jmxExcludingPattern)){
//                return FormValidation.error("Invalid pattern format.");
//            }
            return FormValidation.ok();
        }
        public FormValidation doCheckJmeterCommand(@QueryParameter String jmeterCommand) {
            if (jmeterCommand == null || jmeterCommand.isEmpty() || jmeterCommand.trim().isEmpty()) {
                return FormValidation.error("This blank is required, otherwise Jmeter cannot start.");
            }
            return FormValidation.ok();
        }
        public FormValidation doCheckJmeterArgs(@QueryParameter String jmeterArgs) {
            return FormValidation.ok();
        }
    }

    private String logDirectory;
    private String baseDirectory;
    private boolean disabled;

    /**
     * ANT-style wildcards
     */
    private String jmxIncludingPattern;

    private String jmxExcludingPattern;

    private String jmeterCommand;

    private String jmeterArgs;

    @DataBoundConstructor
    public JmeterPerformanceTester(boolean disabled, String jmxIncludingPattern, String jmxExcludingPattern, String jmeterCommand, String jmeterArgs) {
        this.disabled = disabled;
        this.jmxIncludingPattern = jmxIncludingPattern;
        this.jmxExcludingPattern = jmxExcludingPattern;
        this.jmeterCommand = jmeterCommand;
        this.jmeterArgs = jmeterArgs;
    }

    public void run(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
        if (disabled) {
            listener.getLogger().println("[WARNING] Ignore disabled task: " + this.toString());
            return;
        }
        final EnvVars env = build.getEnvironment(listener);
        final String resultDir = this.getBaseDirectory() == null || this.getBaseDirectory().isEmpty() ? "" : this.getBaseDirectory();
        final String jmeterLogDir = logDirectory == null || logDirectory.isEmpty() ? resultDir : logDirectory;

        final String workspaceDirFullPath = build.getWorkspace().getRemote();

        new File(workspaceDirFullPath + File.separator + resultDir).mkdirs();
        new File(workspaceDirFullPath + File.separator + jmeterLogDir).mkdirs();
        new File(workspaceDirFullPath + File.separator + jmeterLogDir).mkdirs();

        SimpleDateFormat dateFormatForLogName = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        dateFormatForLogName.setTimeZone(TimeZone.getTimeZone("GMT"));

        for (FilePath file : build.getWorkspace().list(env.expand(jmxIncludingPattern), env.expand(jmxExcludingPattern))) {
            // absolute path for each matched file
            String fileFullPath = file.getRemote();
            // get relative path to workspace
            String fileRelativePath = new File(workspaceDirFullPath).toPath().relativize(new File(fileFullPath).toPath()).toString();
            String resultFileName = resultDir + File.separator + file.getBaseName() + ".jtl";
            String logFileName = jmeterLogDir + File.separator + "jmeter-" + file.getBaseName() + dateFormatForLogName.format(new Date()) + ".log";
            // construct command line arguments for a Jmeter execution
            final List<String> cmdArgs = new LinkedList<String>();
            cmdArgs.addAll(Arrays.asList(Commandline.translateCommandline(env.expand(jmeterCommand))));
            cmdArgs.addAll(Arrays.asList(Commandline.translateCommandline(env.expand(jmeterArgs))));
            cmdArgs.add("-n");
            cmdArgs.add("-t");
            cmdArgs.add(fileRelativePath);
            cmdArgs.add("-l");
            cmdArgs.add(resultFileName);
            cmdArgs.add("-j");
            cmdArgs.add(logFileName);

            launcher.getChannel().call(new Callable<Object, IOException>() {
                @Override
                public void checkRoles(RoleChecker checker) throws SecurityException {
                }

                @Override
                public Object call() throws IOException {
                    listener.getLogger().printf("INFO: Launch Jmeter by executing`" + cmdArgs + "`...\n");
                    ProcessBuilder jmeterProcessBuilder = new ProcessBuilder(cmdArgs);
                    jmeterProcessBuilder.environment().putAll(env);
                    jmeterProcessBuilder.redirectError(jmeterProcessBuilder.redirectOutput());
                    Process jmeter = jmeterProcessBuilder.start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(jmeter.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null)
                        listener.getLogger().println(line);
                    try {
                        jmeter.waitFor();
                    } catch (InterruptedException ex) {
                        throw new IOException(ex);
                    }
                    reader.close();
                    return null;
                }
            });
        }
    }

    public String getJmxIncludingPattern() {
        return jmxIncludingPattern;
    }

    public void setJmxIncludingPattern(String jmxIncludingPattern) {
        this.jmxIncludingPattern = jmxIncludingPattern;
    }

    public String getJmxExcludingPattern() {
        return jmxExcludingPattern;
    }

    public void setJmxExcludingPattern(String jmxExcludingPattern) {
        this.jmxExcludingPattern = jmxExcludingPattern;
    }

    public String getJmeterCommand() {
        return jmeterCommand;
    }

    public void setJmeterCommand(String jmeterCommand) {
        this.jmeterCommand = jmeterCommand;
    }

    public String getJmeterArgs() {
        return jmeterArgs;
    }

    public void setJmeterArgs(String jmeterArgs) {
        this.jmeterArgs = jmeterArgs;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @Override
    public String toString() {
        return this.getClass().toString()
                + " - Apache Jmeter Performance test task (command=" + jmeterCommand
                + ", args=" + jmeterArgs
                + ", +files=" + jmxIncludingPattern
                + ", -files=" + jmxExcludingPattern
                + ", resultDir=" + getBaseDirectory() + ")";
    }

    @Override
    public String getLogDirectory() {
        return logDirectory;
    }

    @Override
    public void setLogDirectory(String logDirectory) {
        this.logDirectory = logDirectory;
    }

    @Override
    public String getBaseDirectory() {
        return baseDirectory;
    }

    @Override
    public void setBaseDirectory(String baseDirectory) {
        this.baseDirectory = baseDirectory;
    }
}
