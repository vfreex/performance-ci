package org.jenkinsci.plugins.perfci;

import hudson.model.AbstractProject;
import hudson.model.Action;
import org.kohsuke.stapler.StaplerProxy;

import java.util.logging.Logger;

public class PerfchartsProjectTrendAction implements Action, StaplerProxy {
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

    @Override
    public Object getTarget() {
        return new PerfchartsTrendReport(project);
    }
}
