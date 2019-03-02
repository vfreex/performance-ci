package org.jenkinsci.plugins.perfci.common;

import java.io.File;

/**
 * Created by vfreex on 11/26/15.
 */
public class Constants {
    public final static String PERF_CHARTS_RELATIVE_PATH = "perfcharts";
    public final static String OUTPUT_DIR_RELATIVE_PATH = PERF_CHARTS_RELATIVE_PATH
            + File.separator + "report";
    public final static String CMP_DIR_RELATIVE_PATH = PERF_CHARTS_RELATIVE_PATH
            + File.separator + "comparison";
    public final static String MONO_REPORT_NAME = "mono_report.html";
    public final static String TREND_DIR_RELATIVE_PATH = PERF_CHARTS_RELATIVE_PATH
            + File.separator + "trend";
    public final static String TREND_INPUT_DEFAULT_FILENAME = "trend_input.perftrend";
    public final static String TREND_MONO_REPORT_NAME = "trend_report.html";
}
