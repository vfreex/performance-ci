package com.redhat.jenkins.plugins.perfci.targets;

import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.util.QuotedStringTokenizer;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class SSHTarget extends AbstractDescribableImpl<SSHTarget> implements MonitoringTarget<SSHTarget>, Serializable {

    private final static int DEFAULT_TIMEOUT = 600000;
    @DataBoundSetter
    private String host;
    @DataBoundSetter
    private String user;
    @DataBoundSetter
    private int port = 22;
    @DataBoundSetter
    private String fingerprint;
    @DataBoundSetter
    private String dir;
    @DataBoundSetter
    private String password;
    @DataBoundSetter
    private List<String> keys;
    private int timeout = DEFAULT_TIMEOUT;
    private int connectTimeout = DEFAULT_TIMEOUT;
    private int joinTimeout = DEFAULT_TIMEOUT;

    private transient TaskListener listener;

    private transient SSHClient ssh;

    @DataBoundConstructor
    public SSHTarget(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getKeys() {
        return keys;
    }

    public void setKeys(List<String> keys) {
        this.keys = keys;
    }

    @Override
    public void connect() throws IOException {
        ssh = new SSHClient();
        ssh.setConnectTimeout(connectTimeout);
        ssh.setTimeout(timeout);
        if (fingerprint != null && !fingerprint.isEmpty()) {
            ssh.addHostKeyVerifier(fingerprint);
        } else {
            printConsole("WARNING: Fingerprint is not specified. The SSH connection is vulnerable to MIIT attack!");
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
        }
        printConsole(
                "INFO: Connecting to host \"" + host + ":" + port + "\"...");
        ssh.connect(host);
        try {
            if (password != null && !password.isEmpty()) {
                printConsole("INFO: Authenticating user \"" + user + "\" with password...");
                ssh.authPassword(user, password);
            } else {
                printConsole("INFO: Authenticating user \"" + user + "\" with public key...");
                if (keys != null && !keys.isEmpty())
                    ssh.authPublickey(user, keys.toArray(new String[0]));
                else
                    ssh.authPublickey(user);
            }
            printConsole("INFO: Authenticated to SSH target \"" + host + ":" + port + "\".");
        } catch (Exception e) {
            printConsole("ERROR: Unable to authenticate to SSH target \"" + host + ":" + port + "\".");
            ssh.disconnect();
            throw e;
        }
    }

    @Override
    public boolean isReady() {
        return this.ssh != null && this.ssh.isConnected() && this.ssh.isAuthenticated();
    }

    @Override
    public void disconnect() throws IOException, InterruptedException {
        if (ssh == null)
            return;
        ssh.close();
    }

    @Override
    public RunCommandResult run(String executable, List<String> arguments, Map<String, String> envVars) throws IOException, InterruptedException {
        if (ssh == null || !ssh.isConnected() || !ssh.isAuthenticated()) {
            throw new ConnectionException("SSH target is not connected or authenticated.");
        }
        try (Session session = ssh.startSession()) {
            if (envVars != null) {
                for (String k : envVars.keySet())
                    session.setEnvVar(k, envVars.get(k));
            }
            List<String> fullCommand = Lists.newArrayList(executable);
            fullCommand.addAll(arguments);
            String commandForPrint = String.join(", ", fullCommand);
            //printConsole("Running " + commandForPrint);
            StringBuffer commandBuffer = new StringBuffer(QuotedStringTokenizer.quote(executable));
            for (String arg : arguments) {
                commandBuffer.append(' ');
                QuotedStringTokenizer.quote(commandBuffer, arg);
            }
            Session.Command cmd = session.exec(commandBuffer.toString());
            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future<String> stdoutFuture = executor.submit(() -> {
                StringBuffer sb = new StringBuffer();
                BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmd.getInputStream()));
                stdoutReader.lines().forEach(line -> {
                    sb.append(line).append("\n");
                    printConsole(line);
                });
                return sb.toString();
            });
            Future<String> stderrFuture = executor.submit(() -> {
                StringBuffer sb = new StringBuffer();
                BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmd.getErrorStream()));
                stderrReader.lines().forEach(line -> {
                    sb.append(line).append("\n");
                    printConsole(line);
                });
                return sb.toString();
            });

            String out = stdoutFuture.get();
            String err = stderrFuture.get();
            cmd.join(joinTimeout, TimeUnit.MILLISECONDS);
            int exitStatus = cmd.getExitStatus();
            if (exitStatus != 0)
                printConsole("Command " + commandForPrint + " exited with " + Integer.toString(exitStatus));
            return new RunCommandResult(cmd.getExitStatus(), out, err, fullCommand);
        } catch (ExecutionException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }

    @Override
    public List<String> ls(String path) throws IOException, InterruptedException {
        return ssh.newSFTPClient().ls(path).stream().map(file -> file.getName()).collect(Collectors.toList());
    }

    public void download(String remote, String local, boolean deleteAfterDownload) throws IOException, InterruptedException {
        SFTPClient sftp = ssh.newSFTPClient();
        sftp.get(remote, local);
        if (deleteAfterDownload)
            sftp.rm(remote);
    }

    public void upload(String local, String remote) throws IOException, InterruptedException {
        SFTPClient sftp = ssh.newSFTPClient();
        sftp.put(local, remote);
    }

    @Override
    public String toString() {
        return String.format("SSH target %s@%s:%s", user, host, port);
    }

    @Override
    public void setListener(TaskListener listener) {
        this.listener = listener;
    }

    @Override
    public TaskListener getListener() {
        return this.listener;
    }

    @Override
    public void close() throws Exception {
        if (this.ssh != null && this.ssh.isConnected())
            this.ssh.close();
    }

    @Extension
    @Symbol("sshTarget")
    public static class DescriptorImpl extends Descriptor<SSHTarget> {
        @Override
        public String getDisplayName() {
            return "SSH monitoring target";
        }
    }

}
