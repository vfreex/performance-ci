package org.jenkinsci.plugins.perfci.action;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.perfci.common.Constants;
import org.jenkinsci.plugins.perfci.common.IOHelper;
import org.jenkinsci.plugins.perfci.model.PerfchartsComparisonReport;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Created by vfreex on 11/26/15.
 */
public class PerfchartsBuildReportAction implements Action {
    private static final String ACTION_NAME = "Performance Report";
    private static final String ACTION_PATH = "performance-report";
    private static final String ACTION_ICON = "graph.gif";
    private static final Logger LOGGER = Logger
            .getLogger(PerfchartsBuildReportAction.class.getName());

    public final AbstractBuild<?, ?> build;

    public PerfchartsBuildReportAction(AbstractBuild<?, ?> build) {
        this.build = build;
    }

    public void doMonoReport(StaplerRequest request, StaplerResponse response)
            throws IOException {
        String buildPath = build.getRootDir().getAbsolutePath();
        String outputPath = buildPath + File.separator + Constants.OUTPUT_DIR_RELATIVE_PATH;
        String monoReportFile = outputPath + File.separator + Constants.MONO_REPORT_NAME;
        response.setContentType("text/html; charset=UTF-8");
        IOHelper.copySteam(new FileInputStream(monoReportFile),
                response.getOutputStream());
    }

    public void doGetDataJs(StaplerRequest request, StaplerResponse response)
            throws IOException {
        String buildPath = build.getRootDir().getAbsolutePath();
        String outputPath = buildPath+ File.separator +
                Constants.OUTPUT_DIR_RELATIVE_PATH;
        String dataFile = outputPath+ File.separator +"tmp"+ File.separator +
                "data.js";
        response.setContentType("text/javascript");
        try {
            IOHelper.copySteam(new FileInputStream(dataFile),
                    response.getOutputStream());
        } catch (FileNotFoundException ex) {
            response.sendError(404);
        }
    }

    public void doGetDestBuilds(StaplerRequest request, StaplerResponse response)
            throws IOException {
        response.setContentType("text/json");
        JSONObject result = new JSONObject();
        /*
		 * if (build.getResult().isWorseThan(Result.SUCCESS)) {
		 * result.put("error", 1); result.put("errorMessage",
		 * "This is an unsuccessful build."); return; }
		 */
        JSONArray builds = new JSONArray();
        for (AbstractBuild<?, ?> buildItem : build.getProject().getBuilds()) {
            // whenever the build number larger or not, can get the builds in
            // "sel_build"
			/*
			 * if (buildItem.number >= build.number ||
			 * buildItem.getResult().isWorseThan(Result.SUCCESS)) continue;
			 */
            if (buildItem.number == build.number)
                continue;
            JSONObject buildItemJSON = new JSONObject();
            buildItemJSON.put("value", buildItem.number);
            buildItemJSON.put("text", "#" + buildItem.number + " - "
                    + buildItem.getDisplayName());
            builds.add(buildItemJSON);
        }
        result.put("error", 0);
        result.put("result", builds);
        IOUtils.write(result.toString(), response.getOutputStream());
    }

    public PerfchartsComparisonReport getComparisonReport(int buildNumber) {
        return new PerfchartsComparisonReport(build.getProject(), build, build
                .getProject().getBuildByNumber(buildNumber));
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
}
