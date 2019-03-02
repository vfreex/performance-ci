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
@Deprecated
public class PerfchartsBuildReportExecutor extends AbstractExternalProgramExecutor implements Serializable {
    private String cgtCommand;
    private String currentDirectory;
    private TimeZone timeZone;
    private String inputDir;
    private String outputDir;
    private String monoReportPath;
    private String startOffset;
    private String testDuration;
    /*
    private String fromTime;
    private String toTime;
    */
    private String excludedTransactionPattern;
    private PrintStream redirectedOutput;

    public PerfchartsBuildReportExecutor(String cgtCommand, String currentDirectory, TimeZone timeZone, String inputDir, String outputDir,
                                         String monoReportPath, String startOffset, String testDuration,/*String fromTime, String toTime,*/
                                         String excludedTransactionPattern, PrintStream redirectedOutput) {
        this.cgtCommand = cgtCommand;
        this.currentDirectory = currentDirectory;
        this.redirectedOutput = redirectedOutput;
        this.timeZone = timeZone;
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        this.monoReportPath = monoReportPath;
        this.startOffset = startOffset;
        this.testDuration = testDuration;
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
        arguments.add("-d");
        arguments.add(outputDir);
        arguments.add("-o");
        arguments.add(monoReportPath);
        if (timeZone != null) {
            arguments.add("-z");
            arguments.add(timeZone.getID());
        }
        if (startOffset != null && !startOffset.isEmpty()) {
            arguments.add("-s");
            arguments.add(startOffset);
        }
        if (testDuration != null && !testDuration.isEmpty()) {
            arguments.add("-l");
            arguments.add(testDuration);
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

    public String getStartOffset() {
        return startOffset;
    }

    public void setStartOffset(String startOffset) {
        this.startOffset = startOffset;
    }

    public String getTestDuration() {
        return testDuration;
    }

    public void setTestDuration(String testDuration) {
        this.testDuration = testDuration;
    }
}
