package com.redhat.jenkins.plugins.perfci.targets;

import java.io.Serializable;
import java.util.List;

public class RunCommandResult implements Serializable {
    private int status;
    private String out;
    private String err;
    private List<String> command;

    public RunCommandResult(int status, String out, String err, List<String> command) {
        this.status = status;
        this.out = out;
        this.err = err;
        this.command = command;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getOut() {
        return out;
    }

    public void setOut(String out) {
        this.out = out;
    }

    public String getErr() {
        return err;
    }

    public void setErr(String err) {
        this.err = err;
    }

    public List<String> getCommand() {
        return command;
    }

    public void setCommand(List<String> command) {
        this.command = command;
    }

    @Override
    public String toString() {
        return "{" +
                "status=" + status +
                ", out='" + out + '\'' +
                ", err='" + err + '\'' +
                ", command=" + command +
                '}';
    }
}
