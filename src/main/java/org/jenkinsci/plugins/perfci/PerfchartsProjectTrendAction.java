package org.jenkinsci.plugins.perfci;

import hudson.model.AbstractProject;
import hudson.model.Action;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PerfchartsProjectTrendAction implements Action/*, StaplerProxy*/ {
    private static final String ACTION_NAME = "Performance Trend Report";
    private static final String ACTION_PATH = "trendReport";
    private static final String ACTION_ICON = "graph.gif";
    private static final Logger LOGGER = Logger
            .getLogger(PerfchartsProjectTrendAction.class.getName());
    public final AbstractProject<?, ?> project;

    public PerfchartsProjectTrendAction(AbstractProject<?, ?> project) {
        this.project = project;
//		for (AbstractBuild<?, ?> build : project.getBuilds()) {
//			LOGGER.info(build + ", class=" + build.getClass());
//		}
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

    /*@Override
    public Object getTarget() {
        return new PerfchartsTrendReport(project);
    }*/

    public void doSubmit(StaplerRequest request, StaplerResponse response) throws Exception {
        response.setContentType("text/json");
        JSONObject result = new JSONObject();
        //response.getWriter().println(request.getRestOfPath());
        String input = request.getParameter("builds");
        Pattern pattern = Pattern.compile("\\d+(-\\d+)*(,\\d+(-\\d+)*)*");
        //response.getWriter().println("<p>" + input + "</p>\n");
        if (!pattern.matcher(input).matches()) {
            //response.getWriter().println("<p>ERROR_VALIDATION</p>\n");
            result.put("error", 1);
            result.put("errorMessage", "invalid input format");
            response.getWriter().write(result.toString());
            return;
        }
        String[] buildParts = input.split(",");

        // check cache file existence
        String urlID = TrendReportManager.concatParts(buildParts);
        String reportID = TrendReportManager.calculateReportID(urlID);
        if (!TrendReportManager.reportExists(project, reportID) && !TrendReportManager.generateReport(project, buildParts)) {
            result.put("error", 2);
            result.put("errorMessage", "build failed");
            response.getWriter().write(result.toString());
            return;
        }
        result.put("error", 0);
        result.put("url", "show/" + urlID);
        response.getWriter().write(result.toString());
        //response.sendRedirect(302, "show/" + sb.toString());
    }

    public PerfchartsTrendReport getShow(String urlID) throws Exception {
        return new PerfchartsTrendReport(project,urlID);
    }
}
