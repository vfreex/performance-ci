package org.jenkinsci.plugins.perfci;

import hudson.model.AbstractProject;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;

public class PerfchartsTrendReport extends PerfchartsReport {
    private final static Logger LOGGER = Logger
            .getLogger(PerfchartsTrendReport.class.getName());
    private String urlID;
    private String reportID;

    public PerfchartsTrendReport(AbstractProject<?, ?> project, String urlID) {
        super(project);
        this.urlID = urlID;
        this.reportID = TrendReportManager.calculateReportID(urlID);
    }

    @Override
    public String getDataJSPath() {
        return "trendDataJS";
    }

    public void doMonoReport(StaplerRequest request, StaplerResponse response)
            throws Exception {
        response.setContentType("text/html");
        //synchronized (project) {
        String dataFile = TrendReportManager
                .getTrendMonoReportPath(project, reportID);
        if (!new File(dataFile).exists()) {
            IOUtils.write("<h1>No Trend Report Found.</h1><p> Requested File: " + dataFile + "</p>",
                    response.getOutputStream());
            return;
        }
        IOHelpers.copySteam(new FileInputStream(dataFile),
                response.getOutputStream());
        // }
    }

    public void doTrendDataJS(StaplerRequest request, StaplerResponse response)
            throws Exception {
        response.setContentType("text/javascript");
        synchronized (project) {
            String dataFile = TrendReportManager.getTrendDataJSPath(project, reportID);
            if (!new File(dataFile).exists() && !generate()) {
                return;
            }
            IOHelpers.copySteam(new FileInputStream(dataFile),
                    response.getOutputStream());
        }
    }

    private boolean generate() throws Exception  {
        return TrendReportManager.generateReport(project, urlID);
    }

    public void doGenerate(StaplerRequest request, StaplerResponse response)
            throws Exception {
        String builds = request.getParameter("builds");
        response.setContentType("text/json");
        JSONObject result = new JSONObject();
        boolean success = generate();
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

    public void doRefresh(StaplerRequest request, StaplerResponse response)
            throws Exception {
        String builds = request.getParameter("builds");
        response.setContentType("text/json");
        JSONObject result = new JSONObject();
        boolean success = generate();
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

    private static void writeJSON(StaplerResponse response, JSONObject json)
            throws IOException {
        IOUtils.write(json.toString(), response.getOutputStream());
    }

    public String getUrlID() {
        return urlID;
    }

    public String getBuildIDs() {
        return urlID.replace('_', ',');
    }

    public String getReportID() {
        return reportID;
    }
}
