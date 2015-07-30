package org.jenkinsci.plugins.perfci;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class TrendReportManager {
    private static final Logger LOGGER = Logger
            .getLogger(TrendReportManager.class.getName());

    public static String getTrendDirPath(AbstractProject<?, ?> project) {
        return IOHelpers.concatPathParts(project.getBuildDir()
                .getAbsolutePath(), Constants.TREND_DIR_RELATIVE_PATH);
    }

    public static String getTrendMonoReportPath(AbstractProject<?, ?> project) {
        return IOHelpers.concatPathParts(getTrendDirPath(project),
                Constants.TREND_MONO_REPORT_NAME);
    }

    public static String getTrendDataJSPath(AbstractProject<?, ?> project) {
        return IOHelpers.concatPathParts(getTrendDirPath(project), "tmp",
                "data.js");
    }

    public static boolean generateReport(AbstractProject<?, ?> project,
                                         String tagsString) throws IOException, InterruptedException {
        synchronized (project) {
            LOGGER.info("generating trend report for project '"
                    + project.getName() + "', target tags are '" + tagsString
                    + "'.");

            String buildRootPath = project.getBuildDir().getAbsolutePath();
            String trendDirPath = IOHelpers.concatPathParts(buildRootPath,
                    Constants.TREND_DIR_RELATIVE_PATH);
            String trendMonoReportPath = IOHelpers.concatPathParts(
                    trendDirPath, Constants.TREND_MONO_REPORT_NAME);
            String trendInputFilePath = IOHelpers.concatPathParts(
                    buildRootPath, Constants.TREND_INPUT_RELATIVE_PATH);

            new File(trendDirPath).mkdirs();
            File trendInputFile = new File(trendInputFilePath);
            trendInputFile.delete();
            OutputStreamWriter trendInputWriter = new OutputStreamWriter(
                    new FileOutputStream(trendInputFile));

            Set<String> targetTags = new HashSet<String>();
            if (tagsString != null && !tagsString.isEmpty()) {
                for (String tag : tagsString.split(","))
                    targetTags.add(tag);
            }

            for (AbstractBuild<?, ?> build : project.getBuilds()) {
                Set<String> buildTags = TagManager.loadTagsForBuild(build);
                if (!targetTags.contains("#" + build.number)
                        && !match(buildTags, targetTags)) {
                    // LOGGER.info("skipped build#" + build.number + " tags: "
                    // + String.join(",", buildTags) + ", target tags: "
                    // + String.join(",", targetTags));
                    continue;
                }
                LOGGER.info("matched build#" + build.number + " tags: "
                        + Utilities.joinArray(",", buildTags)
                        + ", target tags: "
                        + Utilities.joinArray(",", targetTags));
                String perfDataPath = getPerfDataPathForBuild(build);
                if (!new File(perfDataPath).exists()) {
                    LOGGER.info("Skip build#"
                            + build.number
                            + " for trend report generation: Its perf&res report is not found.");
                    continue;
                }
                trendInputWriter.write("\"" + build.getDisplayName().replace("\"", "\"\"") + "\"");
                trendInputWriter.write(",");
                trendInputWriter.write(perfDataPath);
                trendInputWriter.write("\n");
            }
            trendInputWriter.flush();
            trendInputWriter.close();

            PerfchartsRecorder.DescriptorImpl desc = (PerfchartsRecorder.DescriptorImpl) Jenkins
                    .getInstance().getDescriptor(PerfchartsRecorder.class);

            CgtTrendProxy cgtTrend = new CgtTrendProxy(desc.getCgtHome(),
                    desc.getCgtLib(), desc.getCgtLog(), null,
                    trendInputFilePath, trendDirPath, trendMonoReportPath,
                    null, null);
            if (!cgtTrend.run()) {
                LOGGER.warning("Perf trend report generated failed.");
                return false;
            }
            LOGGER.info("Perf trend report generated successfully.");
            return true;
        }
    }

    private static boolean match(Set<String> set, Set<String> target) {
        if (target == null || target.isEmpty())
            return true;
        for (String elem : target) {
            if (elem == null || elem.isEmpty())
                continue;
            if (set.contains(elem))
                return true;
        }
        return false;
    }

    private static String getPerfDataPathForBuild(AbstractBuild<?, ?> build) {
        return IOHelpers.concatPathParts(build.getRootDir().getAbsolutePath(),
                Constants.OUTPUT_DIR_RELATIVE_PATH, "tmp", "subreports",
                "Performance.json");
    }
}
