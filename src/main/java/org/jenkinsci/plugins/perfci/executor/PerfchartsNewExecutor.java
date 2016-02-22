package org.jenkinsci.plugins.perfci.executor;

import org.apache.tools.ant.types.Commandline;
import org.jenkinsci.plugins.perfci.common.IOHelper;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by vfreex on 11/25/15.
 */
public class PerfchartsNewExecutor extends AbstractExternalProgramExecutor implements Serializable {
    private String cgtCommand;
    private String reportType;
    private String currentDirectory;
    private TimeZone timeZone;
    private String inputDir;
    private String outputDir;
    private String monoReportPath;
    private String excludedTransactionPattern;
    private PrintStream redirectedOutput;

    public PerfchartsNewExecutor(String cgtCommand, String reportType, String currentDirectory, TimeZone timeZone, String inputDir, String outputDir,
                                 String monoReportPath,
                                 String excludedTransactionPattern, PrintStream redirectedOutput) {
        this.cgtCommand = cgtCommand;
        this.reportType = reportType;
        this.currentDirectory = currentDirectory;
        this.redirectedOutput = redirectedOutput;
        this.timeZone = timeZone;
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        this.monoReportPath = monoReportPath;
        /*
        this.fromTime = fromTime;
		this.toTime = toTime;
		*/
        this.excludedTransactionPattern = excludedTransactionPattern;
    }

    @Override
    public int run() throws IOException, InterruptedException {
        List<String> arguments = new LinkedList<String>();
        arguments.addAll(Arrays.asList(Commandline.translateCommandline(cgtCommand)));
        arguments.add("gen");
        arguments.add(reportType);
        arguments.add("-d");
        arguments.add(outputDir);
        arguments.add("-o");
        arguments.add(monoReportPath);
        if (timeZone != null) {
            arguments.add("-z");
            arguments.add(timeZone.getID());
        }
        if (excludedTransactionPattern != null
                && !excludedTransactionPattern.isEmpty()) {
            arguments.add("-e");
            arguments.add(excludedTransactionPattern);
        }
        arguments.add(inputDir);

        if (redirectedOutput != null)
            redirectedOutput.println("INFO: PerfchartsBuildReportExecutor - Will exec `" + arguments
                    + "`");

        ProcessBuilder cgtProcessBuilder = new ProcessBuilder(arguments);
        if (currentDirectory != null)
            cgtProcessBuilder.directory(new File(currentDirectory));

        if (redirectedOutput != null)
            redirectedOutput.println("INFO: PerfchartsBuildReportExecutor - Generating performance test report from '" + inputDir
                    + "'...");
        Process cgtProcess = cgtProcessBuilder.start();
        if (redirectedOutput != null)
            IOHelper.copySteam(cgtProcess.getErrorStream(), redirectedOutput);
        return cgtProcess.waitFor();
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public String getExcludedTransactionPattern() {
        return excludedTransactionPattern;
    }

    public void setExcludedTransactionPattern(String excludedTransactionPattern) {
        this.excludedTransactionPattern = excludedTransactionPattern;
    }

    public PrintStream getRedirectedOutput() {
        return redirectedOutput;
    }

    public void setRedirectedOutput(PrintStream redirectedOutput) {
        this.redirectedOutput = redirectedOutput;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }
}
