package org.jenkinsci.plugins.perfci.executor;

import org.apache.tools.ant.types.Commandline;
import org.jenkinsci.plugins.perfci.common.IOHelper;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

public class PerfchartsTrendReportExecutor extends AbstractExternalProgramExecutor {
    private final static Logger LOGGER = Logger.getLogger(PerfchartsTrendReportExecutor.class
            .getName());
    private String command;
    private TimeZone timeZone;
    private String inputFile;
    private String outputDir;
    private String monoReportPath;
    private String fromTime;
    private String toTime;
    private PrintStream redirectedOutput;

    public PerfchartsTrendReportExecutor(
            String command, TimeZone timeZone, String inputFile, String outputDir,
            String monoReportPath, String fromTime, String toTime) {
        this.command = command;
        this.timeZone = timeZone;
        this.inputFile = inputFile;
        this.outputDir = outputDir;
        this.monoReportPath = monoReportPath;
        this.fromTime = fromTime;
        this.toTime = toTime;
    }

    @Override
    public int run() throws IOException, InterruptedException {
        List<String> arguments = new ArrayList<String>();
        arguments.addAll(Arrays.asList(Commandline.translateCommandline(command)));
        arguments.add("gen");
        arguments.add("perf-trend");
        arguments.add("-d");
        arguments.add(outputDir);
        arguments.add("-o");
        arguments.add(monoReportPath);
        if (timeZone != null) {
            arguments.add("-z");
            arguments.add(timeZone.getID());
        }
        if (fromTime != null && !fromTime.isEmpty()) {
            arguments.add("-f");
            arguments.add(fromTime);
        }
        if (toTime != null && !toTime.isEmpty()) {
            arguments.add("-t");
            arguments.add(toTime);
        }
        arguments.add(inputFile);
        ProcessBuilder cgtProcessBuilder = new ProcessBuilder(arguments);

        LOGGER.info("Generating trend report from '" + inputFile + "'...");
        if (redirectedOutput != null)
            redirectedOutput.println("INFO: Generating trend report from '"
                    + inputFile + "'...");

        Process cgtProcess = cgtProcessBuilder.start();
        PrintStream errStream = redirectedOutput == null ? System.err
                : redirectedOutput;
        IOHelper.copySteam(cgtProcess.getErrorStream(), errStream);
        return cgtProcess.waitFor();
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public String getFromTime() {
        return fromTime;
    }

    public void setFromTime(String fromTime) {
        this.fromTime = fromTime;
    }

    public String getToTime() {
        return toTime;
    }

    public void setToTime(String toTime) {
        this.toTime = toTime;
    }

    public PrintStream getRedirectedOutput() {
        return redirectedOutput;
    }

    public void setRedirectedOutput(PrintStream redirectedOutput) {
        this.redirectedOutput = redirectedOutput;
    }

}
