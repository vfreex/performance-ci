package org.jenkinsci.plugins.perfci;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Action;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class PerfchartsComparisonReport extends PerfchartsReport {
	private AbstractBuild<?, ?> sourceBuild;
	private AbstractBuild<?, ?> destBuild;

	public PerfchartsComparisonReport(AbstractProject<?, ?> project,
			AbstractBuild<?, ?> sourceBuild, AbstractBuild<?, ?> destBuild) {
		super(project);
		if (sourceBuild == null)
			throw new NullPointerException("sourceBuild");
		this.sourceBuild = sourceBuild;
		if (destBuild == null)
			throw new NullPointerException("destBuild");
		this.destBuild = destBuild;
	}

	private void buildReport() throws IOException, InterruptedException {
		String sourceBuildPath = sourceBuild.getRootDir().getAbsolutePath();
		String destBuildPath = destBuild.getRootDir().getAbsolutePath();
		// get the displayname, all the report will show the display name. If it is not setted, will show build_id (the default display name)
		String sourceDisplayName = sourceBuild.getDisplayName();
		String destDisplayName = destBuild.getDisplayName();
                System.out.println("sourceDisplayName is " + sourceDisplayName);
		System.out.println("destDisplayName is " + destDisplayName);
		String inputPath = IOHelpers.concatPathParts(sourceBuildPath,
				Constants.CMP_DIR_RELATIVE_PATH,
				Integer.toString(destBuild.number));
	        new File(inputPath).mkdirs();
		// will clean the rawdata dir first
                IOHelpers.cleanInputPath(inputPath);
	        String inputFileName = "Perf-build " + sourceDisplayName + "_vs_"
				+ destDisplayName + ".perfcmp";
		String inputFilePath = IOHelpers.concatPathParts(inputPath,
				inputFileName);
		File inputFile = new File(inputFilePath);
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(inputFile)));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		writer.write("SOURCE,"
				+ sourceBuild.number
				+ ","
				+ sourceBuild.getDisplayName()
				+ ","
				+ IOHelpers.concatPathParts(sourceBuildPath,
						Constants.OUTPUT_DIR_RELATIVE_PATH,
						"tmp/subreports/Performance.json") + "\n");
		writer.write("DEST,"
				+ destBuild.number
				+ ","
				+ destBuild.getDisplayName()
				+ ","
				+ IOHelpers.concatPathParts(destBuildPath,
						Constants.OUTPUT_DIR_RELATIVE_PATH,
						"tmp/subreports/Performance.json") + "\n");
		writer.flush();
		writer.close();

		PerfchartsRecorder.DescriptorImpl desc = (PerfchartsRecorder.DescriptorImpl) Jenkins
				.getInstance().getDescriptor(PerfchartsRecorder.class);
		String outputPath = IOHelpers.concatPathParts(inputPath, "report");
		CgtCmpProxy cgtCmp = new CgtCmpProxy(desc.getCgtHome(),
				desc.getCgtLib(), desc.getCgtLog(), inputPath, outputPath,
				IOHelpers.concatPathParts(outputPath, Constants.MONO_REPORT_NAME));
		cgtCmp.run();
	}

	@Override
	public String getDataJSPath() {
		return "getDataJs";
	}

	public void doGetDataJs(StaplerRequest request, StaplerResponse response)
			throws Exception {
		response.setContentType("text/javascript");
		String sourceBuildPath = sourceBuild.getRootDir().getAbsolutePath();
		//get the display name of build
		String sourceDisplayName = sourceBuild.getDisplayName();
		String destDisplayName = destBuild.getDisplayName();
                String inputFileName = "Perf-build " +sourceDisplayName + "_vs_"
                                + destDisplayName + ".perfcmp";
		//locate the perf-build*.perfcmp
		String inputPath = IOHelpers.concatPathParts(sourceBuildPath,
				Constants.CMP_DIR_RELATIVE_PATH,
				Integer.toString(destBuild.number));
                String inputFilePath = IOHelpers.concatPathParts(inputPath, inputFileName);
		String outputPath = IOHelpers.concatPathParts(inputPath, "report");
		String dataFilePath = IOHelpers.concatPathParts(outputPath, "tmp",
				"data.js");
		File inputFile = new File(inputFilePath);
		File dataFile = new File(dataFilePath);
		// if the *.perfcmp does not exist, will rerun buildReport()
		if (!dataFile.exists() | !inputFile.exists()) {
			buildReport();
		}
		IOHelpers.copySteam(new FileInputStream(dataFile),
				response.getOutputStream());
	}

	public void doMonoReport(StaplerRequest request, StaplerResponse response)
			throws Exception {
		response.setContentType("text/html");
		// synchronized (project) {
		String sourceBuildPath = sourceBuild.getRootDir().getAbsolutePath();
		// String destBuildPath = destBuild.getRootDir().getAbsolutePath();
		String inputPath = IOHelpers.concatPathParts(sourceBuildPath,
				Constants.CMP_DIR_RELATIVE_PATH,
				Integer.toString(destBuild.number));
		String outputPath = IOHelpers.concatPathParts(inputPath, "report");
		String monoReportFilePath = IOHelpers.concatPathParts(outputPath,
				Constants.MONO_REPORT_NAME);
		if (!new File(monoReportFilePath).exists()) {
			IOUtils.write("<h1>No Comparison Report Found.</h1>",
					response.getOutputStream());
			return;
		}
		IOHelpers.copySteam(new FileInputStream(monoReportFilePath),
				response.getOutputStream());
	}

	public void doGenerate(StaplerRequest request, StaplerResponse response)
			throws Exception {
		JSONObject result = new JSONObject();
		response.setContentType("text/json");
		try {
		buildReport();
		} catch (Exception e) {
			result.put("error", 1);
			result.put("errorMessage", "Fail to generate trend report.");
			writeJSON(response, result);
			return;
		}
		result.put("error", 0);
		result.put("errorMessage", "Generated.");
		writeJSON(response, result);
	}

	private static void writeJSON(StaplerResponse response, JSONObject json)
			throws IOException {
		IOUtils.write(json.toString(), response.getOutputStream());
	}
	
	public void doGetDestBuilds(StaplerRequest request, StaplerResponse response)
			throws IOException {
		response.setContentType("text/json");
		JSONObject result = new JSONObject();
		if (sourceBuild.getResult().isWorseThan(Result.SUCCESS))
		{
			result.put("error", 1);
			result.put("errorMessage", "This is an unsuccessful build.");
			return;
		}
		JSONArray builds = new JSONArray();
		for (AbstractBuild<?, ?> buildItem : sourceBuild.getProject().getBuilds()) {
			if (buildItem.number >= sourceBuild.number || buildItem.getResult().isWorseThan(Result.SUCCESS))
				continue;
			JSONObject buildItemJSON = new JSONObject();
			buildItemJSON.put("value", buildItem.number);
			buildItemJSON.put("text", "#" + buildItem.number + " - "
					+ buildItem.getDisplayName());
			builds.add(buildItemJSON);
		}
		result.put("error", 0);
		result.put("result", builds);
		//result.put("resultCount", builds.size());
		writeJSON(response, result);
	}

	public AbstractBuild<?, ?> getSourceBuild() {
		return sourceBuild;
	}

	public void setSourceBuild(AbstractBuild<?, ?> sourceBuild) {
		this.sourceBuild = sourceBuild;
	}

	public AbstractBuild<?, ?> getDestBuild() {
		return destBuild;
	}

	public void setDestBuild(AbstractBuild<?, ?> destBuild) {
		this.destBuild = destBuild;
	}

}
