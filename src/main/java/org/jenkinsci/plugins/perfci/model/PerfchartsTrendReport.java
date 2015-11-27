package org.jenkinsci.plugins.perfci.model;

import hudson.model.AbstractProject;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.perfci.common.IOHelper;
import org.jenkinsci.plugins.perfci.common.TrendReportManager;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Logger;

public class PerfchartsTrendReport {
    private final static Logger LOGGER = Logger
            .getLogger(PerfchartsTrendReport.class.getName());
    private String urlID;
    private String reportID;
    public final AbstractProject<?,?> project;

    public PerfchartsTrendReport(AbstractProject<?, ?> project, String urlID) {
        this.urlID = urlID;
        this.project = project;
        this.reportID = TrendReportManager.calculateReportID(urlID);
    }

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
            LOGGER.warning("Trend report `" + dataFile + "` does not exist.");
            response.sendError(404);
            return;
        }
        IOHelper.copySteam(new FileInputStream(dataFile),
                response.getOutputStream());
    }

    public void doTrendDataJS(StaplerRequest request, StaplerResponse response)
            throws Exception {
        response.setContentType("text/javascript");
        synchronized (project) {
            String dataFile = TrendReportManager.getTrendDataJSPath(project, reportID);
            if (!new File(dataFile).exists()) {
                try {
                    generate();
                }catch (Exception ex) {
                    response.sendError(404);
                    return;
                }
            }
            IOHelper.copySteam(new FileInputStream(dataFile),
                    response.getOutputStream());
        }
    }

    private void generate() throws Exception  {
        TrendReportManager.generateReport(project, urlID);
    }

    public void doRefresh(StaplerRequest request, StaplerResponse response)
            throws Exception {
        String builds = request.getParameter("builds");
        response.setContentType("text/json");
        JSONObject result = new JSONObject();
        try {
            generate();
        }catch (Exception ex) {
            ex.printStackTrace();
            result.put("error", 1);
            result.put("errorMessage", "Failed to generate trend report." + ex.getMessage());
            writeJSON(response, result);
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
