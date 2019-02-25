package com.redhat.jenkins.plugins.perfci.targets;

import com.redhat.jenkins.plugins.perfci.common.Listenable;
import hudson.model.Describable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface MonitoringTarget<T extends Describable<T>> extends Describable<T>, AutoCloseable, Listenable {
    void connect() throws InterruptedException, IOException;
    boolean isReady();
    void disconnect() throws InterruptedException, IOException;
    RunCommandResult run(String executable, List<String> arguments, Map<String, String> envVars) throws InterruptedException, IOException;
    void download(String remote, String local, boolean deleteAfterDownload) throws IOException, InterruptedException;
    void upload(String local, String remote) throws IOException, InterruptedException;
    List<String> ls(String path) throws IOException, InterruptedException;
}
