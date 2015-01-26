package org.jenkinsci.plugins.perfci;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

public class CgtTrendProxy {
	private final static Logger LOGGER = Logger.getLogger(CgtTrendProxy.class
			.getName());
	private String cgtHome;
	private String cgtLib;
	private String cgtLog;
	private TimeZone timeZone;
	private String inputFile;
	private String outputDir;
	private String monoReportPath;
	private String fromTime;
	private String toTime;

	public CgtTrendProxy(String cgtHome, String cgtLib, String cgtLog,
			TimeZone timeZone, String inputFile, String outputDir,
			String monoReportPath, String fromTime, String toTime) {
		this.cgtHome = cgtHome;
		this.cgtLib = cgtLib;
		this.cgtLog = cgtLog;
		this.timeZone = timeZone;
		this.inputFile = inputFile;
		this.outputDir = outputDir;
		this.monoReportPath = monoReportPath;
		this.fromTime = fromTime;
		this.toTime = toTime;
	}

	public boolean run() throws IOException, InterruptedException {
		List<String> arguments = new ArrayList<String>();
		arguments.add(cgtHome + File.separator
				+ Constants.CGT_TREND_RELATIVE_PATH);
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

		if (cgtHome != null && !cgtHome.isEmpty())
			cgtProcessBuilder.environment().put("CGT_HOME", cgtHome);
		if (cgtLib != null && !cgtLib.isEmpty())
			cgtProcessBuilder.environment().put("CGT_LIB", cgtLib);
		if (cgtLog != null && !cgtLog.isEmpty())
			cgtProcessBuilder.environment().put("CGT_LOG", cgtLog);

		LOGGER.info("generating trend report from '" + inputFile + "'...");

		Process cgtProcess = cgtProcessBuilder.start();
		int returnCode = cgtProcess.waitFor();
		boolean success = returnCode == 0;
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				cgtProcess.getInputStream()));
		String line;
		while ((line = reader.readLine()) != null) {
			System.out.println(line);
		}
		reader = new BufferedReader(new InputStreamReader(
				cgtProcess.getErrorStream()));
		while ((line = reader.readLine()) != null) {
			System.err.println(line);
		}
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

}
