package org.jenkinsci.plugins.perfci.model;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.org.apache.tools.tar.TarInputStream;
import hudson.remoting.Callable;
import hudson.util.FormValidation;
import jenkins.security.Roles;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.InMemorySourceFile;
import org.apache.tools.tar.TarEntry;
import org.jenkinsci.plugins.perfci.common.BaseDirectoryRelocatable;
import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.*;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

public class NmonMonitor extends ResourceMonitor implements BaseDirectoryRelocatable {
    // private Object monfile;
    private final static int TIMEOUT = 600000;
    private final static int MAX_TRIES = 5;
    private final String host;
    private final String name;
    private final String password;
    private final String interval;
    //private String outputPath;
    private final String fingerprint;
    private String baseDirectory;
    private boolean isDisabled;

    // Fields in config.jelly must match the parameter names in the
    // "DataBoundConstructor"
    @DataBoundConstructor
    public NmonMonitor(String host, String name, String password,
                       String interval, String fingerprint, boolean isDisabled) {
        this.host = host;
        this.name = name;
        this.password = password;
        this.interval = interval;
        this.fingerprint = fingerprint;
        this.isDisabled = isDisabled;
    }

    private static String getOutputDir(String projectName, int buildID) {
        return getBuildDir(projectName, buildID) + "/monitoring";
    }

    private static String getBuildDir(String projectName, int buildID) {
        return getProjectDir(projectName) + "/" + buildID;
    }

    private static String getProjectDir(String projectName) {
        return "/tmp/jenkins-perfci/jobs/" + projectName;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public String getHost() {
        return host;
    }

    public String getInterval() {
        return interval;
    }

    private void tryStart(String projectName, int buildId, BuildListener listener) throws IOException, InterruptedException {
        URL currentVersionFile = getClass()
                .getResource(
                        "/org/jenkinsci/plugins/perfci/model/NmonMonitor/jenkins-perfci/version.txt");
        String currentNMONVersion =
                org.apache.commons.io.IOUtils.readLines(currentVersionFile.openStream()).get(0).trim();
        assert !currentNMONVersion.isEmpty();
        listener.getLogger().println(
                "INFO: Current NMON monitoring toolkit version: " + currentNMONVersion);

        SSHClient client = new SSHClient();
        client.setConnectTimeout(TIMEOUT);
        client.setTimeout(TIMEOUT);
        if (fingerprint != null && !fingerprint.isEmpty())
            client.addHostKeyVerifier(fingerprint);
        else
            client.addHostKeyVerifier(new PromiscuousVerifier());
        try {
            listener.getLogger().println(
                    "INFO: Connecting to host \"" + host + "\"...");
            client.connect(host);
            listener.getLogger().println("INFO: Authenticating...");
            if (password == null || password.isEmpty())
                client.authPublickey(name);
            else
                client.authPassword(name, password);
            Session.Command cmd;
            Session session = client.startSession();
            listener.getLogger().println(
                    "INFO: Checking existence for NMON monitoring toolkit.");
            cmd = session
                    .exec("cat /tmp/jenkins-perfci/version.txt");
            cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
            session.close();

            String installedVersion = cmd.getExitStatus() == 0 ? IOUtils.readFully(cmd.getInputStream()).toString()
                    .trim() : null;
            if (installedVersion == null) {
                listener.getLogger().println(
                        "INFO: NMON monitoring toolkit was not installed .");
            } else if (!currentNMONVersion.equals(installedVersion)) {
                listener.getLogger().println(
                        "INFO: NMON monitoring toolkit need to be reinstalled. Current version: " + currentNMONVersion + " / Installed version: " + installedVersion);
            }
            if (!currentNMONVersion.equals(installedVersion)) {
                listener.getLogger().println(
                        "INFO: Uploading NMON monitoring toolkit...");
                {
                    final URL nmonfile = getClass()
                            .getResource(
                                    "/org/jenkinsci/plugins/perfci/model/NmonMonitor/perfci-nmon-monitor-upload.tar.gz");
                    final InputStream in = nmonfile.openStream();
                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    org.apache.commons.io.IOUtils.copy(in, out);
                    in.close();
                    final byte[] bytes = out.toByteArray();
                    out.close();
                    client.newSCPFileTransfer().upload(
                            new InMemorySourceFile() {
                                public String getName() {
                                    return "jenkins-perfci-upload.tar.gz";
                                }

                                public long getLength() {
                                    return bytes.length;
                                }

                                public InputStream getInputStream()
                                        throws IOException {
                                    return new ByteArrayInputStream(bytes);
                                }
                            }, "/tmp/");
                    listener
                            .getLogger().println(
                            "INFO: jenkins-perfci-upload.tar.gz has been uploaded to target host.");
                }
                listener
                        .getLogger().println(
                        "INFO: extracting files from 'perfci-monitor-upload.tar.gz'...");
                session = client.startSession();
                cmd = session
                        .exec("cd /tmp && rm -rf /tmp/jenkins-perfci && tar -xzvf /tmp/jenkins-perfci-upload.tar.gz");
                cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
                session.close();
                if (cmd.getExitStatus() != 0) {
                    listener
                            .getLogger().println(
                            "ERROR: Cannot extract files from 'jenkins-perfci-upload.tar.gz'.");
                    return;
                }
            }
            listener.getLogger().println(
                    "INFO: Starting NMON & cpuload_monitor deamons...");
            String remoteLogDir = getOutputDir(projectName, buildId);
            session = client.startSession();
            int intervalValue = Integer.parseInt(interval);
            cmd = session.exec("/tmp/jenkins-perfci/bin/start_monitor '"
                    + projectName + "' '" + buildId + "' " + intervalValue);
            cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
            session.close();
            if (cmd.getExitStatus() != 0) {
                listener.getLogger().println(
                        "INFO: error code=" + cmd.getExitStatus());
                throw new IOException("ERROR: Failed to call start_monitor on monitored host.");
            }
        } finally {
            client.close();
        }
    }

    private void tryStop(String projectName, int buildId, String workspaceDir, BuildListener listener) throws IOException, InterruptedException {
        //String projectDir = getProjectDir(projectName);
        SSHClient client = new SSHClient();
        client.setConnectTimeout(TIMEOUT);
        client.setTimeout(TIMEOUT);
        if (fingerprint != null && !fingerprint.isEmpty())
            client.addHostKeyVerifier(fingerprint);
        else
            client.addHostKeyVerifier(new PromiscuousVerifier());
        try {
            listener.getLogger().println(
                    "INFO: Connecting to host \"" + host + "\"...");
            client.connect(host);
            listener.getLogger().println("INFO: Authenticating...");
            if (password == null || password.isEmpty())
                client.authPublickey(name);
            else
                client.authPassword(name, password);
            listener.getLogger().println("INFO: Try killing NMON deamon...");
            Session.Command cmd;
            Session session;
            session = client.startSession();
            cmd = session.exec("/tmp/jenkins-perfci/bin/stop_monitor '"
                    + projectName + "'");
            cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
            session.close();
            if (cmd.getExitStatus() != 0 && cmd.getExitStatus() != 1) {
                listener.getLogger().println("ERROR: Failed to call stop_monitor.");
                return;// false;
            }

            String resultDir = this.getBaseDirectory() == null || this.getBaseDirectory().isEmpty() ? "" : this.getBaseDirectory();

            String pathOnAgent = workspaceDir + File.separator + resultDir;

            if (new File(pathOnAgent).mkdirs()) {
                listener.getLogger().println("INFO: Create directory '" + pathOnAgent + "' in workspace.");
            }
            String buildPath = getBuildDir(projectName, buildId);
            String gzipFilePath = buildPath + "/monitoring.tar.gz";

            listener.getLogger().println("INFO: Compress monitoring results '" + buildPath + "/monitoring'...");
            session = client.startSession();
            cmd = session.exec("cd '" + buildPath + "/monitoring/' && tar -czf ../monitoring.tar.gz .");
            cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
            session.close();
            if (cmd.getExitStatus() != 0) {
                listener.getLogger().println("ERROR: Compress failed. Failed to call tar. Exit code = " + cmd.getExitStatus());
                return;
            }

            String to = pathOnAgent + "/monitoring-" + UUID.randomUUID().toString() + ".tar.gz";
            listener.getLogger().println(
                    "INFO: Copying monitoring results into Jenkins workspace ('" + to
                            + "')...");
            client.newSCPFileTransfer().download(gzipFilePath, to);
            listener.getLogger().println("INFO: Decompress...");
            //try {
            File destGzipFile = new File(to);
            TarInputStream tarIn = new TarInputStream(new GZIPInputStream(new FileInputStream(destGzipFile)));
            TarEntry tarEnt;
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((tarEnt = tarIn.getNextEntry()) != null) {
                File file = tarEnt.getFile();
                File decompressedFile = new File(pathOnAgent, tarEnt.getName());
                if (!tarEnt.isDirectory()) {
                    OutputStream out = new FileOutputStream(decompressedFile);
                    while ((bytesRead = tarIn.read(buffer, 0, buffer.length)) > 0) {
                        out.write(buffer, 0, bytesRead);
                    }
                    out.flush();
                    out.close();
                    listener.getLogger().println("INFO: File '" + tarEnt.getName() + "' has been decompressed into workspace.");
                } else {
                    decompressedFile.mkdirs();
                    listener.getLogger().println("INFO: Create directory '" + decompressedFile.getAbsolutePath() + "'.");
                }
            }
            destGzipFile.delete();
        } finally {
            client.close();
        }
    }

    public String getFingerprint() {
        return fingerprint;
    }

    @Override
    public void start(final AbstractBuild<?, ?> build, Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
        if (isDisabled) {
            listener.getLogger().println("WARNING: PerfCI will not start NMON monitor (" + this.host + ") according to your configuration.");
            return;
        }
        final String projectName = build.getProject().getName();
        final int buildId = build.number;
//        for (int i = 1; i <= MAX_TRIES; ++i) {
//        listener.getLogger().println(
//                "INFO: Starting NMON monitor... (try " + i + " of "
//                        + MAX_TRIES + ")");
        launcher.getChannel().call(new hudson.remoting.Callable<Object, IOException>() {
            @Override
            public void checkRoles(RoleChecker checker) throws SecurityException {
            }

            @Override
            public Object call() throws IOException {
                try {
                    listener.getLogger().println("INFO: Starting NMON monitor...");
                    tryStart(projectName, buildId, listener);
                    listener.getLogger().println("INFO: NMON monitor started.");
                } catch (InterruptedException e) {
                    listener.getLogger().println(
                            "ERROR: Failed to start NMON monitor.");
                    throw new IOException(e);
                }
                return null;
            }
        });
    }

    @Override
    public void stop(final AbstractBuild<?, ?> build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
        if (isDisabled) {
            listener.getLogger().println("WARNING: PerfCI will not stop NMON monitor (" + this.host + ") according to your configuration.");
            return;
        }
//        try {
//            for (int i = 1; i <= MAX_TRIES; ++i) {
//        listener.getLogger().println("INFO: Stopping NMON monitor... (try " + i + " of "
//                + MAX_TRIES + ")");
        final String projectName = build.getProject().getName();
        final int buildId = build.number;
        final String workspaceDir = build.getWorkspace().getRemote();
        //listener.getLogger().println("INFO: Stopping NMON monitor...");
        launcher.getChannel().call(new Callable<Object, IOException>() {
            @Override
            public void checkRoles(RoleChecker checker) throws SecurityException {
            }

            @Override
            public Object call() throws IOException {
                try {
                    listener.getLogger().println("INFO: Stopping NMON monitor...");
                    tryStop(projectName, buildId, workspaceDir, listener);
                } catch (InterruptedException e) {
                    listener.getLogger().println(
                            "ERROR: Failed to stop NMON monitor.");
                    throw new IOException(e);
                }
                return null;
            }
        });
    }

    @Override
    public boolean isEnabled() {
        return !getIsDisabled();
    }

    @Override
    public void checkRoles(RoleChecker checker,
                           Callable<?, ? extends SecurityException> callable)
            throws SecurityException {
        checker.check(callable, Roles.SLAVE, Roles.MASTER);
    }

    public boolean getIsDisabled() {
        return isDisabled;
    }

    public void setIsDisabled(boolean isDisabled) {
        this.isDisabled = isDisabled;
    }

    public String getBaseDirectory() {
        return baseDirectory;
    }

    public void setBaseDirectory(String baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    @Extension
    public static class DescriptorImpl extends ResourceMonitorDescriptor {

        @Override
        public String getDisplayName() {
            return "NMON Monitor";
        }

        public FormValidation doCheckHost(@QueryParameter String host) {
            if (host == null || host.isEmpty()) {
                return FormValidation.error("Host name can't be empty");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckName(@QueryParameter String name) {
            if (name == null || name.isEmpty()) {
                return FormValidation.error("User name can't be empty");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckPassword(@QueryParameter String
                                                      password) {
            if (password == null || password.isEmpty()) {
                return FormValidation.ok();
            }
            return FormValidation.warning("Storing your SSH password to a Jenkins server is a bad practice, because other people are able to inspect your password though the source code of this web page. We recommend you to use public key authentication.");
        }

        public FormValidation doCheckFingerprint(
                @QueryParameter String fingerprint) {
            if (fingerprint == null || fingerprint.isEmpty()) {
                return FormValidation.warning("If you ignore this blank, the SSH connection to your monitored host is vulnerable to a man-in-the-middle (MITM) attack.");
            }
            if (!Pattern.matches("[\\da-fA-F]{1,2}(?:\\:[\\da-fA-F]{1,2}){15}",
                    fingerprint)) {
                return FormValidation.error("Invalid fingerprint format");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckInterval(@QueryParameter String interval) {
            if (interval == null || interval.isEmpty()) {
                return FormValidation.error("Interval can't be empty");
            }
            if (!Pattern.matches("\\d{1,10}", interval)) {
                return FormValidation.error("Invalid interval format");
            }
            if (Integer.parseInt(interval) <= 0) {
                return FormValidation.error("Invalid interval range");
            }
            return FormValidation.ok();
        }
    }
}
