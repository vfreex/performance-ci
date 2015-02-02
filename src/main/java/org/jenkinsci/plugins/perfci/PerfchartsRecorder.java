package org.jenkinsci.plugins.perfci;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class PerfchartsRecorder extends Recorder {
	private final static String RECORDER_DISPLAY_NAME = "Publish Performance & Resource Monitoring Report";
	private final static Logger LOGGER = Logger
			.getLogger(PerfchartsRecorder.class.getName());

	@Extension
	public final static class DescriptorImpl extends
			BuildStepDescriptor<Publisher> {
		private String cgtHome;// = System.getenv("CGT_HOME");
		private String cgtLib;// = System.getenv("CGT_LIB");
		private String cgtLog;// = System.getenv("CGT_LOG");
		private String defaultTimeZone;// = TimeZone.getDefault().getID();

		public DescriptorImpl() {
			load();
		}

		@Override
		public boolean isApplicable(
				@SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return RECORDER_DISPLAY_NAME;
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData)
				throws FormException {
			req.bindJSON(this, formData);

			save();
			return super.configure(req, formData);
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

		public String getDefaultTimeZone() {
			return defaultTimeZone;
		}

		public void setDefaultTimeZone(String defaultTimeZone) {
			this.defaultTimeZone = defaultTimeZone;
		}
	}

	private String inputPattern;
	private String timeZone;
	private String fromTime;
	private String toTime;

	@DataBoundConstructor
	public PerfchartsRecorder(String inputPattern, String timeZone,
			String fromTime, String toTime) {
		this.inputPattern = inputPattern != null && !inputPattern.isEmpty() ? inputPattern
				: "**/*.jtl;**/*.nmon;**/*.load;**/*.conf";
		this.timeZone = timeZone;
		this.fromTime = fromTime;
		this.toTime = toTime;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws IOException, InterruptedException {
		if (build.getResult() != Result.SUCCESS){
			LOGGER.warning("build.getResult(): " + build.getResult().toString()); 
			return false;
		}
		DescriptorImpl desc = getDescriptor();
		TimeZone tz = timeZone != null && !timeZone.isEmpty() ? TimeZone
				.getTimeZone(timeZone)
				: (desc.getDefaultTimeZone() != null
						&& !desc.getDefaultTimeZone().isEmpty() ? TimeZone
						.getTimeZone(desc.getDefaultTimeZone()) : TimeZone
						.getDefault());
		FilePath workspace = build.getWorkspace();
		List<FilePath> remoteFiles = IOHelpers.locateFiles(workspace,
				inputPattern);
		if (remoteFiles.isEmpty()) {
			LOGGER.warning("No test results found.");
			return false;
		}
		LOGGER.info("Copy test results to master...");
		IOHelpers.copyToBuildDir(build, remoteFiles);
		LOGGER.info("Copy test results complete.");
		String buildPath = build.getRootDir().getAbsolutePath();
		String inputPath = IOHelpers.concatPathParts(buildPath,
				Constants.INPUT_DIR_RELATIVE_PATH);
		String outputPath = IOHelpers.concatPathParts(buildPath,
				Constants.OUTPUT_DIR_RELATIVE_PATH);
		String monoReportFile = IOHelpers.concatPathParts(outputPath,
				Constants.MONO_REPORT_NAME);
		CgtPerfProxy cgtPerf = new CgtPerfProxy(desc.getCgtHome(),
				desc.getCgtLib(), desc.getCgtLog(), tz, inputPath, outputPath,
				monoReportFile, fromTime, toTime);

		if (!cgtPerf.run()) {
			LOGGER.severe("Perf&Res report generated failed.");
			return false;
		}
		build.addAction(new PerfchartsBuildAction(build));
		LOGGER.info("Perf&Res report generated successfully.");

//		String trendOutputPath = IOHelpers.concatPathParts(buildPath,
//				Constants.TREND_DIR_RELATIVE_PATH);
//		String trendMonoReportFile = IOHelpers.concatPathParts(trendOutputPath,
//				Constants.TREND_MONO_REPORT_NAME);
//		String trendInputReportFile = IOHelpers.concatPathParts(buildPath,
//				Constants.TREND_INPUT_RELATIVE_PATH);
//		new File(IOHelpers.concatPathParts(buildPath,
//				Constants.TREND_DIR_RELATIVE_PATH)).mkdirs();
//		File trendInputFile = new File(trendInputReportFile);
//		trendInputFile.delete();
//		OutputStreamWriter trendInputWriter = new OutputStreamWriter(
//				new FileOutputStream(trendInputFile));
//		AbstractProject<?, ?> project = build.getProject();
//		for (AbstractBuild<?, ?> b : project.getBuilds()) {
//			trendInputWriter.write(Integer.toString(b.number));
//			trendInputWriter.write(",");
//			trendInputWriter.write(IOHelpers.concatPathParts(b.getRootDir()
//					.getAbsolutePath(), Constants.OUTPUT_DIR_RELATIVE_PATH,
//					"tmp", "subreports", "Performance.json"));
//			trendInputWriter.write("\n");
//		}
//		trendInputWriter.flush();
//		trendInputWriter.close();
//		CgtTrendProxy cgtTrend = new CgtTrendProxy(desc.getCgtHome(),
//				desc.getCgtLib(), desc.getCgtLog(), tz, trendInputReportFile,
//				trendOutputPath, trendMonoReportFile, null, null);
//		if (!cgtTrend.run()) {
//			LOGGER.warning("Perf trend report generated failed.");
//			// return false;
//		} else {
//			LOGGER.info("Perf trend report generated successfully.");
//		}
		return true;
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	@Override
	public Collection<? extends Action> getProjectActions(
			AbstractProject<?, ?> project) {
		List<Action> actions = new ArrayList<Action>();
		actions.add(new PerfchartsProjectTrendAction(project));
		return actions;
	}

	public String getInputPattern() {
		return inputPattern;
	}

	public void setInputPattern(String inputPattern) {
		this.inputPattern = inputPattern;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(String timeZone) {
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
