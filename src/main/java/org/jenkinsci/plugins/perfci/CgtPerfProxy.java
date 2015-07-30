package org.jenkinsci.plugins.perfci;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

public class CgtPerfProxy {
	private final static Logger LOGGER = Logger.getLogger(CgtPerfProxy.class
			.getName());
	private String cgtHome;
	private String cgtLib;
	private String cgtLog;
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

	public CgtPerfProxy(String cgtHome, String cgtLib, String cgtLog,
			TimeZone timeZone, String inputDir, String outputDir,
			String monoReportPath, String startOffset, String testDuration,/*String fromTime, String toTime,*/
			String excludedTransactionPattern) {
		this.cgtHome = cgtHome;
		this.cgtLib = cgtLib;
		this.cgtLog = cgtLog;
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

	public boolean run() throws IOException, InterruptedException {
		List<String> arguments = new ArrayList<String>();
		arguments.add(cgtHome + File.separator
				+ Constants.CGT_PERF_RELATIVE_PATH);
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
		/*
		if (fromTime != null && !fromTime.isEmpty()) {
			arguments.add("-f");
			arguments.add(fromTime);
		}
		if (toTime != null && !toTime.isEmpty()) {
			arguments.add("-t");
			arguments.add(toTime);
		}
		*/
		if (excludedTransactionPattern != null
				&& !excludedTransactionPattern.isEmpty()) {
			arguments.add("-e");
			arguments.add(excludedTransactionPattern);
		}
		arguments.add(inputDir);
		ProcessBuilder cgtProcessBuilder = new ProcessBuilder(arguments);

		if (cgtHome != null && !cgtHome.isEmpty())
			cgtProcessBuilder.environment().put("CGT_HOME", cgtHome);
		if (cgtLib != null && !cgtLib.isEmpty())
			cgtProcessBuilder.environment().put("CGT_LIB", cgtLib);
		if (cgtLog != null && !cgtLog.isEmpty())
			cgtProcessBuilder.environment().put("CGT_LOG", cgtLog);

		LOGGER.info("Generating report from '" + inputDir + "'...");
		if (redirectedOutput != null)
			redirectedOutput.println("Generating report from '" + inputDir
					+ "'...");
		Process cgtProcess = cgtProcessBuilder.start();
		if (redirectedOutput != null) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					cgtProcess.getErrorStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				redirectedOutput.println(line);
			}
		}
		int returnCode = cgtProcess.waitFor();
		boolean success = returnCode == 0;

		// String line;
		// while ((line = reader.readLine()) != null) {
		// System.out.println(line);
		// }
		// reader = new BufferedReader(new InputStreamReader(
		// cgtProcess.getErrorStream()));
		// while ((line = reader.readLine()) != null) {
		// System.err.println(line);
		// }
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

	public TimeZone getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(TimeZone timeZone) {
		this.timeZone = timeZone;
	}
    /*
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
	*/

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
