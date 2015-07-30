package org.jenkinsci.plugins.perfci;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class CgtCmpProxy {
    private final static Logger LOGGER = Logger.getLogger(CgtCmpProxy.class
            .getName());
    private String cgtHome;
    private String cgtLib;
    private String cgtLog;
    private String inputFile;
    private String outputDir;
    private String monoReportPath;
    private PrintStream redirectedOutput;

    public CgtCmpProxy(String cgtHome, String cgtLib, String cgtLog,
                       String inputFile, String outputDir, String monoReportPath) {
        this.cgtHome = cgtHome;
        this.cgtLib = cgtLib;
        this.cgtLog = cgtLog;
        this.inputFile = inputFile;
        this.outputDir = outputDir;
        this.monoReportPath = monoReportPath;
    }

    public boolean run() throws IOException, InterruptedException {
        List<String> arguments = new ArrayList<String>();
        arguments.add(cgtHome + File.separator
                + Constants.CGT_CMP_RELATIVE_PATH);
        arguments.add("-d");
        arguments.add(outputDir);
        arguments.add("-o");
        arguments.add(monoReportPath);
        arguments.add(inputFile);
        ProcessBuilder cgtProcessBuilder = new ProcessBuilder(arguments);

        if (cgtHome != null && !cgtHome.isEmpty())
            cgtProcessBuilder.environment().put("CGT_HOME", cgtHome);
        if (cgtLib != null && !cgtLib.isEmpty())
            cgtProcessBuilder.environment().put("CGT_LIB", cgtLib);
        if (cgtLog != null && !cgtLog.isEmpty())
            cgtProcessBuilder.environment().put("CGT_LOG", cgtLog);

        LOGGER.info("Generating comparison report from '" + inputFile + "'...");
        if (redirectedOutput != null)
            redirectedOutput.println("INFO: Generating comparison report from '"
                    + inputFile + "'...");

        Process cgtProcess = cgtProcessBuilder.start();
        PrintStream errStream = redirectedOutput == null ? System.err
                : redirectedOutput;
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                cgtProcess.getErrorStream()));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                errStream.println(line);
            }
        } finally {
            if (errStream != null)
                errStream.close();
        }

        int returnCode = cgtProcess.waitFor();
        boolean success = returnCode == 0;
        return success;
    }

    public String getCgtHome() {
        return cgtHome;
    }

    public void setCgtHome(String cgtHome) {
        this.cgtHome = cgtHome;
    }

    public String getCgtLib() {
        return cgtLib;
    }

    public void setCgtLib(String cgtLib) {
        this.cgtLib = cgtLib;
    }

    public String getCgtLog() {
        return cgtLog;
    }

    public void setCgtLog(String cgtLog) {
        this.cgtLog = cgtLog;
    }

    public PrintStream getRedirectedOutput() {
        return redirectedOutput;
    }

    public void setRedirectedOutput(PrintStream redirectedOutput) {
        this.redirectedOutput = redirectedOutput;
    }

}
