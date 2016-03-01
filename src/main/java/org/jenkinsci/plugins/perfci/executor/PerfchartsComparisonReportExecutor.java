package org.jenkinsci.plugins.perfci.executor;

import org.apache.tools.ant.types.Commandline;
import org.jenkinsci.plugins.perfci.common.IOHelper;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class PerfchartsComparisonReportExecutor extends AbstractExternalProgramExecutor {
    private final static Logger LOGGER = Logger.getLogger(PerfchartsComparisonReportExecutor.class
            .getName());
    private String command;
    private String inputFile;
    private String outputDir;
    private String monoReportPath;
    private PrintStream redirectedOutput;


    public PerfchartsComparisonReportExecutor(String command, String inputFile, String outputDir, String monoReportPath) {
        this.command = command;
        this.inputFile = inputFile;
        this.outputDir = outputDir;
        this.monoReportPath = monoReportPath;
    }

    public int run() throws IOException, InterruptedException {
        List<String> arguments = new ArrayList<String>();
        arguments.addAll(Arrays.asList(Commandline.translateCommandline(command)));
        arguments.add("-d");
        arguments.add(outputDir);
        arguments.add("-o");
        arguments.add(monoReportPath);
        arguments.add(inputFile);
        ProcessBuilder cgtProcessBuilder = new ProcessBuilder(arguments);

        LOGGER.info("Generating comparison report from '" + inputFile + "'...");
        if (redirectedOutput != null)
            redirectedOutput.println("INFO: Generating comparison report from '"
                    + inputFile + "'...");

        Process cgtProcess = cgtProcessBuilder.start();
        PrintStream errStream = redirectedOutput == null ? System.err
                : redirectedOutput;
        if (errStream != null)
            IOHelper.copySteam(cgtProcess.getErrorStream(), errStream);
        return cgtProcess.waitFor();
    }

    public PrintStream getRedirectedOutput() {
        return redirectedOutput;
    }

    public void setRedirectedOutput(PrintStream redirectedOutput) {
        this.redirectedOutput = redirectedOutput;
    }

}
