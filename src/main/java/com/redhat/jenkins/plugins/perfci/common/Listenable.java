package com.redhat.jenkins.plugins.perfci.common;

import hudson.model.TaskListener;

public interface Listenable {
    void setListener(TaskListener listener);
    TaskListener getListener();

    default void printConsole(String s) {
        TaskListener listener = getListener();
        if (listener == null)
            return;
        listener.getLogger().println(s);
    }
}
