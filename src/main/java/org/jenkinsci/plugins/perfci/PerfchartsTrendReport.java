package org.jenkinsci.plugins.perfci;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import hudson.model.AbstractProject;

public class PerfchartsTrendReport extends PerfchartsReport {
	private final static Logger LOGGER = Logger.getLogger(PerfchartsTrendReport.class.getName());
	private String tags;
	public PerfchartsTrendReport(AbstractProject<?, ?> project) {
		super(project);
		// TODO Auto-generated constructor stub
	}

	@Override
	public String getDataJSPath() {
		return "trendDataJS";
	}

	public void doMonoReport(StaplerRequest request, StaplerResponse response)
			throws Exception {
		synchronized (project) {
			String dataFile = TrendReportManager
					.getTrendMonoReportPath(project);
			if (!new File(dataFile).exists()) {
				IOUtils.write("<h1>No Trend Report Found.</h1>", response.getOutputStream());
				return;
			}
			IOHelpers.copySteam(new FileInputStream(dataFile),
					response.getOutputStream());
		}
	}

	public void doTrendDataJS(StaplerRequest request, StaplerResponse response)
			throws Exception {
		synchronized (project) {
			String dataFile = TrendReportManager.getTrendDataJSPath(project);
			if (!new File(dataFile).exists()) {
				return;
			}
			IOHelpers.copySteam(new FileInputStream(dataFile),
					response.getOutputStream());
		}
	}

	public void doGenerate(StaplerRequest request, StaplerResponse response)
			throws Exception {
		response.setContentType("text/json");
		String tagsString = request.getParameter("tags");
		if (tagsString == null)
			tagsString = "";
		JSONObject result = new JSONObject();
		if (!tagsString.isEmpty() && !TagManager.isTagsStringValid(tagsString)) {
			result.put("error", 1);
			result.put("errorMessage", "Invalid tag list format.");
			writeJSON(response, result);
			return;
		}
		boolean success = TagManager.saveTagsStringOfLastTrendReportForProject(project, tagsString) && TrendReportManager
				.generateReport(project, tagsString);
		if (!success) {
			result.put("error", 1);
			result.put("errorMessage", "Fail to generate trend report.");
			writeJSON(response, result);
			return;
		}
		result.put("error", 0);
		result.put("errorMessage", "Generated.");
		writeJSON(response, result);
	}
	public String getTags() {
		if (tags == null) {
			try {
				this.tags = TagManager.loadTagsStringOfLastTrendReportForProject(project);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return tags;
	}
	public void setTags(String tags) {
		this.tags = tags;
		LOGGER.info("tag is changed to " + tags);
	}
	private static void writeJSON(StaplerResponse response, JSONObject json)
			throws IOException {
		IOUtils.write(json.toString(), response.getOutputStream());
	}

}
