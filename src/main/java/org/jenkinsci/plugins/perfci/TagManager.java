package org.jenkinsci.plugins.perfci;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;

public class TagManager {
	private static final Logger LOGGER = Logger.getLogger(TagManager.class
			.getName());
	private final static String TAG_RELATIVE_PATH = IOHelpers.concatPathParts(
			Constants.PERF_CHARTS_RELATIVE_PATH, "tags");
	public final static Pattern TAGS_PATTERN = Pattern
			.compile("^\\w+([,]\\w+)*$");

	private static String loadTagsString(File tagFile) throws IOException {
		LOGGER.info("load tags file '" + tagFile.getAbsolutePath() + "'.");
		try {
			String content = IOHelpers.readToEnd(tagFile);
			if (!isTagsStringValid(content)) {
				LOGGER.warning("Invalid tags file '"
						+ tagFile.getAbsolutePath() + "'.");
				return null;
			}
			return content;
		} catch (FileNotFoundException ex) {
			return "";
		}
	}

	public static String loadTagsStringForBuild(AbstractBuild<?, ?> build)
			throws IOException {
		File tagFile = new File(IOHelpers.concatPathParts(build.getRootDir()
				.getAbsolutePath(), TAG_RELATIVE_PATH));
		return loadTagsString(tagFile);
	}

	public static Set<String> loadTagsForBuild(AbstractBuild<?, ?> build)
			throws IOException {
		Set<String> set = new HashSet<String>();
		String tagsString = loadTagsStringForBuild(build);
		if (tagsString != null && !tagsString.isEmpty()) {
			for (String tag : tagsString.split(","))
				set.add(tag);
		}
		return set;
	}

	private static boolean saveTagsString(File tagFile, String tagsString)
			throws FileNotFoundException, IOException {
		if (tagsString != null && !tagsString.isEmpty()
				&& !isTagsStringValid(tagsString)) {
			LOGGER.warning("Invalid tags string '" + tagsString + "'.");
			return false;
		}
		if (tagsString == null)
			tagsString = "";
		LOGGER.info("write tags file '" + tagFile.getAbsolutePath() + "'.");
		tagFile.getParentFile().mkdirs();
		IOUtils.write(tagsString, new FileOutputStream(tagFile), "UTF-8");
		return true;
	}

	public static boolean saveTagsStringForBuild(AbstractBuild<?, ?> build,
			String tagsString) throws FileNotFoundException, IOException {
		File tagFile = new File(IOHelpers.concatPathParts(build.getRootDir()
				.getAbsolutePath(), TAG_RELATIVE_PATH));
		return saveTagsString(tagFile, tagsString);
	}

	public static boolean isTagsStringValid(String tags) {
		return TAGS_PATTERN.matcher(tags).matches();
	}

	public static String loadTagsStringOfLastTrendReportForProject(
			AbstractProject<?, ?> proj) throws IOException {
		File tagFile = new File(IOHelpers.concatPathParts(proj.getBuildDir()
				.getAbsolutePath(), TAG_RELATIVE_PATH));
		return loadTagsString(tagFile);
	}

	public static boolean saveTagsStringOfLastTrendReportForProject(
			AbstractProject<?, ?> proj, String tagsString) throws IOException {
		File tagFile = new File(IOHelpers.concatPathParts(proj.getBuildDir()
				.getAbsolutePath(), TAG_RELATIVE_PATH));
		return saveTagsString(tagFile, tagsString);
	}
}
