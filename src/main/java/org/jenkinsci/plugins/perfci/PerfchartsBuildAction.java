package org.jenkinsci.plugins.perfci;

import hudson.model.Action;
import hudson.model.AbstractBuild;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class PerfchartsBuildAction implements Action {
	private static final String ACTION_NAME = "Performance & Monitoring Report";
	private static final String ACTION_PATH = "detailedReport";
	private static final String ACTION_ICON = "graph.gif";
	private static final Logger LOGGER = Logger
			.getLogger(PerfchartsBuildAction.class.getName());
	public final AbstractBuild<?, ?> build;

	private String tags;

	public PerfchartsBuildAction(AbstractBuild<?, ?> build) {
		LOGGER.warning("PerfchartsBuildAction constructed.");
		this.build = build;
	}

	@Override
	public String getDisplayName() {
		return ACTION_NAME;
	}

	@Override
	public String getIconFileName() {
		return ACTION_ICON;
	}

	@Override
	public String getUrlName() {
		return ACTION_PATH;
	}

	public void doMonoReport(StaplerRequest request, StaplerResponse response)
			throws Exception {
		String buildPath = build.getRootDir().getAbsolutePath();
		String outputPath = IOHelpers.concatPathParts(buildPath,
				Constants.OUTPUT_DIR_RELATIVE_PATH);
		String monoReportFile = IOHelpers.concatPathParts(outputPath,
				Constants.MONO_REPORT_NAME);
		IOHelpers.copySteam(new FileInputStream(monoReportFile),
				response.getOutputStream());
	}

	public void doGetDataJs(StaplerRequest request, StaplerResponse response)
			throws Exception {
		String buildPath = build.getRootDir().getAbsolutePath();
		String outputPath = IOHelpers.concatPathParts(buildPath,
				Constants.OUTPUT_DIR_RELATIVE_PATH);
		String dataFile = IOHelpers.concatPathParts(outputPath, "tmp",
				"data.js");
		response.setContentType("text/javascript");
		try {
		IOHelpers.copySteam(new FileInputStream(dataFile),
				response.getOutputStream());
		}catch (FileNotFoundException ex) {
			IOUtils.write("<h1>No Report Found.</h1>", response.getOutputStream());
		}
	}

	public void doUpdateTags(StaplerRequest request, StaplerResponse response)
			throws Exception {
		response.setContentType("text/json");
		String tags = request.getParameter("tags");
		boolean isEmpty = tags == null || tags.isEmpty();
		JSONObject result = new JSONObject();
		if (!isEmpty && !TagManager.isTagsStringValid(tags)) {
			LOGGER.info("tag CANNOT be changed to " + tags);
			result.put("error", 1);
			result.put("errorMessage", "Invalid tag list format.");
			writeJSON(response, result);
			return;
		}
		setTags(tags);
		TagManager.saveTagsStringForBuild(build, tags);
		LOGGER.info("tag is changed to "
				+ (isEmpty ? "EMPTY." : "'" + tags + "'."));
		result.put("error", 0);
		result.put("errorMessage", "changed to '" + tags + "'.");
		writeJSON(response, result);
	}

	private static void writeJSON(StaplerResponse response, JSONObject json)
			throws IOException {
		IOUtils.write(json.toString(), response.getOutputStream());
	}

	public String getTags() {
		if (tags == null) {
			try {
				this.tags = TagManager.loadTagsStringForBuild(build);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return tags;
	}

	public void setTags(String tags) {
		this.tags = tags;
		LOGGER.info("tag is changed to " + tags);
	}

}
