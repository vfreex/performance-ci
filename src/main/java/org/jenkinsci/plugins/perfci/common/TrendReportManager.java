package org.jenkinsci.plugins.perfci.common;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jenkinsci.plugins.perfci.executor.PerfchartsTrendReportExecutor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TrendReportManager {
    private static final Logger LOGGER = Logger
            .getLogger(TrendReportManager.class.getName());

    public static String getTrendDirPath(AbstractProject<?, ?> project, String reportID) {
        return project.getBuildDir()
                .getAbsolutePath() + File.separator + Constants.TREND_DIR_RELATIVE_PATH + File.separator + reportID;
    }

    public static String getTrendMonoReportPath(AbstractProject<?, ?> project, String reportID) {
        return getTrendDirPath(project, reportID) + File.separator +
                Constants.TREND_MONO_REPORT_NAME;
    }

    public static String getTrendDataJSPath(AbstractProject<?, ?> project, String reportID) {
        return getTrendDirPath(project, reportID) + File.separator + "data" + File.separator +
                "data.js";
    }

    public static String concatParts(String[] parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0)
                sb.append("_");
            sb.append(part);
        }
        return sb.toString();
    }

    public static String digest(String alg, byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance(alg);
        sha1.update(bytes);
        byte[] digest = sha1.digest();
        StringBuilder digestSB = new StringBuilder();
        for (byte b : digest) {
            digestSB.append(String.format("%02x", b));
        }
        return digestSB.toString();
    }

    public static String calculateReportID(String[] parts) {
        return calculateReportID(concatParts(parts));
    }

    public static String calculateReportID(String urlIdentifier) {
        try {
            return digest("SHA-1", urlIdentifier.getBytes());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean reportExists(AbstractProject<?, ?> project, String reportID) {
        LOGGER.info(getTrendDataJSPath(project, reportID));
        return new File(getTrendDataJSPath(project, reportID)).exists();
    }

    public static void generateReport(AbstractProject<?, ?> project, String urlID) throws IOException, InterruptedException {
        generateReport(project, urlID == null || urlID.isEmpty() ? new String[0] : urlID.split("_"));
    }

    public static void generateReport(AbstractProject<?, ?> project, String[] parts) throws IOException, InterruptedException {
        String reportID = calculateReportID(parts);
        String buildRootPath = project.getBuildDir().getAbsolutePath();
        String trendDirPath = buildRootPath + File.separator +
                Constants.TREND_DIR_RELATIVE_PATH + File.separator + reportID;
        String trendMonoReportPath =
                trendDirPath + File.separator + Constants.TREND_MONO_REPORT_NAME;
        String trendInputFilePath =
                trendDirPath + File.separator + Constants.TREND_INPUT_DEFAULT_FILENAME;
        new File(trendDirPath).mkdirs();
        OutputStreamWriter inputFileWriter = new OutputStreamWriter(new FileOutputStream(trendInputFilePath));
        CSVPrinter csvPrinter = new CSVPrinter(inputFileWriter, CSVFormat.DEFAULT);

        if (parts == null || parts.length == 0) {
            for (AbstractBuild<?, ?> build : project.getBuilds()) {
                writeBuildInfo(csvPrinter, build);
            }
        } else {
            Pattern rangePattern = Pattern.compile("(\\d+)-(\\d+)");
            for (String part : parts) {
                Matcher m = rangePattern.matcher(part);
                if (m.matches()) { // is a range
                    int first = Integer.parseInt(m.group(1));
                    int second = Integer.parseInt(m.group(2));
                    if (first <= second) { // increase
                        for (int i = first; i <= second; ++i) {
                            writeBuildInfo(csvPrinter, project, i);
                        }
                    } else { //decrease
                        for (int i = first; i >= second; --i) {
                            writeBuildInfo(csvPrinter, project, i);
                        }
                    }
                } else {
                    writeBuildInfo(csvPrinter, project, Integer.parseInt(part));
                }
            }
        }
        csvPrinter.flush();

        PerfchartsTrendReportExecutor cgtTrend = new PerfchartsTrendReportExecutor("perfcharts", null,
                trendInputFilePath, trendDirPath, trendMonoReportPath,
                null, null);
        int retcode = cgtTrend.run();
        if (retcode != 0) {
            throw new IOException("PerfchartsTrendReportExecutor reported an error. code=" + retcode);
        }
        LOGGER.info("Perf trend report generated successfully.");
    }

    private static boolean writeBuildInfo(CSVPrinter csvPrinter, AbstractProject<?, ?> project, int buildNumber) throws IOException {
        AbstractBuild<?, ?> build = project.getBuildByNumber(buildNumber);
        return writeBuildInfo(csvPrinter, build);
    }

    private static boolean writeBuildInfo(CSVPrinter csvPrinter, AbstractBuild<?, ?> build) throws IOException {
        if (build == null) {
            LOGGER.info("Skip unknown build");
            return false;
        }
        String perfDataPath = getPerfDataPathForBuild(build);
        if (!new File(perfDataPath).exists()) {
            LOGGER.info("Skip build #"
                    + build.number
                    + " for trend report generation: Its perf&res report is not found.");
            return false;
        }
        csvPrinter.printRecord(build.getDisplayName(), perfDataPath);
        return true;
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
        return build.getRootDir().getAbsolutePath() + File.separator +
                Constants.OUTPUT_DIR_RELATIVE_PATH + File.separator + "data" + File.separator + "subreports" + File.separator +
                "Performance.json";
    }
}
