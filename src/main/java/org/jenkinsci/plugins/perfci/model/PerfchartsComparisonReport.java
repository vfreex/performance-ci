package org.jenkinsci.plugins.perfci.model;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.perfci.common.Constants;
import org.jenkinsci.plugins.perfci.common.IOHelper;
import org.jenkinsci.plugins.perfci.executor.PerfchartsComparisonReportExecutor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.*;
import java.util.logging.Logger;

public class PerfchartsComparisonReport {
    private final static Logger LOGGER = Logger.getLogger(PerfchartsComparisonReport.class.getName());
    private AbstractBuild<?, ?> sourceBuild;
    private AbstractBuild<?, ?> destBuild;

    public PerfchartsComparisonReport(AbstractProject<?, ?> project,
                                      AbstractBuild<?, ?> sourceBuild, AbstractBuild<?, ?> destBuild) {
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
        // get the display name, all the report will show the display name. If it is not set, will show build_id (the default display name)
        String sourceDisplayName = sourceBuild.getDisplayName();
        String destDisplayName = destBuild.getDisplayName();
        System.out.println("sourceDisplayName is " + sourceDisplayName);
        System.out.println("destDisplayName is " + destDisplayName);
        String inputPath = sourceBuildPath + File.separator +
                Constants.CMP_DIR_RELATIVE_PATH + File.separator + destBuild.number;
        new File(inputPath).mkdirs();
        // will clean the rawdata dir first
        //IOHelpers.cleanInputPath(inputPath);
        String inputFileName = "Perf-build_" + sourceBuild.number + "_vs_"
                + destBuild.number + ".perfcmp";
        String inputFilePath = inputPath + File.separator +
                inputFileName;
        File inputFile = new File(inputFilePath);
        BufferedWriter writer = null;
        writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(inputFile)));
        CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
        csvPrinter.printRecord("SOURCE", sourceBuild.number, sourceBuild.getDisplayName(), sourceBuildPath + File.separator +
                Constants.OUTPUT_DIR_RELATIVE_PATH + File.separator +
                "tmp/subreports/Performance.json");
        csvPrinter.printRecord("DEST", destBuild.number, destBuild.getDisplayName(), destBuildPath + File.separator +
                Constants.OUTPUT_DIR_RELATIVE_PATH + File.separator +
                "tmp/subreports/Performance.json");
        csvPrinter.flush();
        csvPrinter.close();
        String outputPath = inputPath + File.separator + "report";
        PerfchartsComparisonReportExecutor cgtCmp = new PerfchartsComparisonReportExecutor("cgt-cmp", inputPath, outputPath, outputPath + File.separator + Constants.MONO_REPORT_NAME);
        int retcode = cgtCmp.run();
        if (retcode != 0)
            throw new IOException("PerfchartsComparisonReportExecutor reported an error, exit code=" + retcode);
    }

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
        String inputFileName = "build" + sourceBuild.number + "_vs_"
                + destBuild.number + ".perfcmp";
        //locate the perf-build*.perfcmp
        String inputPath = sourceBuildPath + File.separator +
                Constants.CMP_DIR_RELATIVE_PATH + File.separator +
                destBuild.number;
        String inputFilePath = inputPath + File.separator + inputFileName;
        String outputPath = inputPath + File.separator + "report";
        String dataFilePath = outputPath + File.separator + "tmp" + File.separator +
                "data.js";
        File inputFile = new File(inputFilePath);
        File dataFile = new File(dataFilePath);
        // if the *.perfcmp does not exist, will rerun buildReport()
        if (!dataFile.exists() || !inputFile.exists()) {
            buildReport();
        }
        if (!dataFile.exists()) {
            response.sendError(404);
            return;
        }
        IOHelper.copySteam(new FileInputStream(dataFile),
                response.getOutputStream());
    }

    public void doMonoReport(StaplerRequest request, StaplerResponse response)
            throws Exception {
        response.setContentType("text/html");
        // synchronized (project) {
        String sourceBuildPath = sourceBuild.getRootDir().getAbsolutePath();
        // String destBuildPath = destBuild.getRootDir().getAbsolutePath();
        String inputPath = sourceBuildPath + File.separator +
                Constants.CMP_DIR_RELATIVE_PATH + File.separator +
                destBuild.number;
        String outputPath = inputPath + File.separator + "report";
        String monoReportFilePath = outputPath + File.separator +
                Constants.MONO_REPORT_NAME;
        if (!new File(monoReportFilePath).exists()) {
            LOGGER.warning("Mono report file ` " + monoReportFilePath + " ` doesn't exist.");
            response.sendError(404);
            return;
        }
        IOHelper.copySteam(new FileInputStream(monoReportFilePath),
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
            result.put("errorMessage", "Fail to generate trend report." + e.toString());
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

    public void doGetDestBuilds(StaplerRequest request, StaplerResponse response)
            throws IOException {
        response.setContentType("text/json");
        JSONObject result = new JSONObject();
        /*if (sourceBuild.getResult().isWorseThan(Result.SUCCESS))
        {
			result.put("error", 1);
			result.put("errorMessage", "This is an unsuccessful build.");
			return;
		}*/
        JSONArray builds = new JSONArray();
        for (AbstractBuild<?, ?> buildItem : sourceBuild.getProject().getBuilds()) {
            if (buildItem.number >= sourceBuild.number /*|| buildItem.getResult().isWorseThan(Result.SUCCESS)*/)
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
