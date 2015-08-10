package org.jenkinsci.plugins.perfci;

import java.io.File;

public class Constants {
    public final static String CGT_BIN_DEFAULT_RELATIVE_PATH = "bin";
    public final static String CGT_LIB_DEFAULT_RELATIVE_PATH = "lib";
    public final static String CGT_LOG_DEFAULT_RELATIVE_PATH = "log";
    public final static String CGT_PERF_RELATIVE_PATH = CGT_BIN_DEFAULT_RELATIVE_PATH
            + File.separator + "cgt-perf";
    public final static String CGT_TREND_RELATIVE_PATH = CGT_BIN_DEFAULT_RELATIVE_PATH
            + File.separator + "cgt-trend";
    public final static String CGT_CMP_RELATIVE_PATH = CGT_BIN_DEFAULT_RELATIVE_PATH
            + File.separator + "cgt-cmp";
    public final static String CGT_ZABBIX_DL = CGT_BIN_DEFAULT_RELATIVE_PATH
            + File.separator + "cgt-zabbix-dl";
    public final static String PERF_CHARTS_RELATIVE_PATH = "perfcharts";
    public final static String INPUT_DIR_RELATIVE_PATH = PERF_CHARTS_RELATIVE_PATH
            + File.separator + "rawdata";
    public final static String OUTPUT_DIR_RELATIVE_PATH = PERF_CHARTS_RELATIVE_PATH
            + File.separator + "report";
    public final static String CMP_DIR_RELATIVE_PATH = PERF_CHARTS_RELATIVE_PATH
            + File.separator + "comparison";
    public final static String MONO_REPORT_NAME = "mono_report.html";

    public final static String TREND_DIR_RELATIVE_PATH = PERF_CHARTS_RELATIVE_PATH
            + File.separator + "trend";
    public final static String TREND_INPUT_DEFAULT_FILENAME = "trend_input.txt";
    public final static String TREND_INPUT_RELATIVE_PATH = TREND_DIR_RELATIVE_PATH
            + File.separator + "trend_input.txt";
    public final static String TREND_MONO_REPORT_NAME = "trend_report.html";
}
