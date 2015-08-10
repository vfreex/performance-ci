package org.jenkinsci.plugins.perfci;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.TimeZone;
import java.util.logging.Logger;

public class PerfchartsBuildAction implements Action {
    private static final String ACTION_NAME = "Performance Report";
    private static final String ACTION_PATH = "detailedReport";
    private static final String ACTION_ICON = "graph.gif";
    private static final Logger LOGGER = Logger
            .getLogger(PerfchartsBuildAction.class.getName());
    public final AbstractBuild<?, ?> build;

    private String tags;
    private String destBuild;

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
        response.setContentType("text/html");
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
        } catch (FileNotFoundException ex) {
            IOUtils.write("<h1>No Report Found.</h1>",
                    response.getOutputStream());
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

    public void doGetDestBuilds(StaplerRequest request, StaplerResponse response)
            throws IOException {
        response.setContentType("text/json");
        JSONObject result = new JSONObject();
        /*if (build.getResult().isWorseThan(Result.SUCCESS))
        {
			result.put("error", 1);
			result.put("errorMessage", "This is an unsuccessful build.");
			return;
		}*/
        JSONArray builds = new JSONArray();
        for (AbstractBuild<?, ?> buildItem : build.getProject().getBuilds()) {
            //whenever the build number larger or not, can get the builds in "sel_build"
                        /*
			if (buildItem.number >= build.number || buildItem.getResult().isWorseThan(Result.SUCCESS)) 
				continue;
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
        //result.put("resultCount", builds.size());
        writeJSON(response, result);
    }

    // add the startOffset & testDuration in the Performance Report
    public void buildReport(String startOffset, String testDuration)
            throws IOException, InterruptedException {
        String sourceBuildPath = build.getRootDir().getAbsolutePath();
        String sourceDisplayName = build.getDisplayName();

        System.out.println("sourceDisplayName is " + sourceDisplayName);
        String inputPath = IOHelpers.concatPathParts(sourceBuildPath,
                Constants.INPUT_DIR_RELATIVE_PATH);
        String outputPath = IOHelpers.concatPathParts(sourceBuildPath,
                Constants.OUTPUT_DIR_RELATIVE_PATH);
        String monoReportFile = IOHelpers.concatPathParts(outputPath,
                Constants.MONO_REPORT_NAME);
        PerfchartsRecorder.DescriptorImpl des = new PerfchartsRecorder.DescriptorImpl();
        String cgthome = des.getCgtHome();
        String cgtlib = des.getCgtLib();
        String cgtlog = des.getCgtLog();
        TimeZone tz = TimeZone.getTimeZone("UTC");
        CgtPerfProxy cgtPerf = new CgtPerfProxy(cgthome, cgtlib, cgtlog, tz,
                inputPath, outputPath, monoReportFile, startOffset,
                testDuration, "null");
        if (!cgtPerf.run()) {
            System.out.println("cgtperf.run() failed");
        }
    }

    public void doStartOffset(StaplerRequest request, StaplerResponse response)
            throws Exception {
        JSONObject result = new JSONObject();
        response.setContentType("text/json");
        String startOffset = request.getParameter("startOffset");
        String testDuration = request.getParameter("testDuration");

        // check the startOffset & testDuration
        if (!startOffset.matches("[0-9]+") || startOffset == null
                || startOffset.isEmpty()) {
            result.put("errorMessage", "Numberic settings are needed.");
            writeJSON(response, result);
            return;
        }
        if (testDuration.length() > 0 && !testDuration.matches("[0-9]+")) {
            result.put("errorMessage", "Numberic settings are needed.");
            writeJSON(response, result);
            return;
        }
        try {
            buildReport(startOffset, testDuration);
        } catch (Exception e) {
            result.put("error", 1);
            result.put("errorMessage", "Fail to generate performance report."
                    + e.toString());
            writeJSON(response, result);
            e.printStackTrace();
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

    public PerfchartsComparisonReport getComparisonReport(int buildNumber) {
        return new PerfchartsComparisonReport(build.getProject(), build, build
                .getProject().getBuildByNumber(buildNumber));
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

    public String getDestBuild() {
        return destBuild;
    }

    public void setDestBuild(String destBuild) {
        this.destBuild = destBuild;
    }

}
