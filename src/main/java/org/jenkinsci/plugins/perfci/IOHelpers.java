package org.jenkinsci.plugins.perfci;

import hudson.FilePath;
import hudson.model.AbstractBuild;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class IOHelpers {
	private final static Logger LOGGER = Logger.getLogger(IOHelpers.class
			.getName());

	public static boolean isAbsolutePath(String path) {
		return path != null && !path.isEmpty()
				&& (path.startsWith("/") || path.startsWith("file:"));
	}

	public static String concatPathParts(String... parts) {
		if (parts.length == 0)
			return "";
		StringBuilder sb = new StringBuilder(parts[0]);
		for (int i = 1; i < parts.length; i++) {
			sb.append(File.separator).append(parts[i]);
		}
		return sb.toString();
	}
	
	public static String readToEnd(String fileName) throws FileNotFoundException, IOException {
		return readToEnd(new File(fileName));
	}
	
	public static String readToEnd(File file) throws FileNotFoundException,
			IOException {
		FileInputStream in = new FileInputStream(file);
		try {
			String s = readToEnd(in);
			return s;
		} finally {
			in.close();
		}
	}

	public static String readToEnd(InputStream in) throws IOException {
		return readToEnd(new InputStreamReader(in));
	}

	public static String readToEnd(Reader reader) throws IOException {
		BufferedReader br = new BufferedReader(reader);
		StringBuilder sb = new StringBuilder();
		char[] buffer = new char[102400];
		int bytesRead;
		while ((bytesRead = br.read(buffer)) > 0)
			sb.append(buffer, 0, bytesRead);
		return sb.toString();
	}

	public static void copySteam(InputStream in, OutputStream out)
			throws IOException {
		copySteam(in, out, 10240);
	}

	public static void copySteam(InputStream in, OutputStream out,
			int bufferSize) throws IOException {
		byte[] buffer = new byte[bufferSize];
		int bytesRead;
		while ((bytesRead = in.read(buffer)) > 0) {
			out.write(buffer, 0, bytesRead);
		}
		out.flush();
	}

	public static List<FilePath> locateFiles(FilePath workspace, String includes)
			throws IOException, InterruptedException {

		// First use ant-style pattern
		/*
		 * try { FilePath[] ret = workspace.list(includes); if (ret.length > 0)
		 * { return Arrays.asList(ret); }
		 */
		// Agoley : Possible fix, if we specify more than one result file
		// pattern
		// try {
		String parts[] = includes.split("\\s*[;:,]+\\s*");
		List<FilePath> files = new ArrayList<FilePath>();
		for (String path : parts) {
			FilePath[] ret = workspace.list(path);
			if (ret.length > 0) {
				files.addAll(Arrays.asList(ret));
			}
		}
		// if (!files.isEmpty())
		return files;

		// } catch (IOException e) {
		// }

		// Agoley: seems like this block doesn't work
		// If it fails, do a legacy search
		// ArrayList<FilePath> files = new ArrayList<FilePath>();
		// String parts[] = includes.split("\\s*[;:,]+\\s*");
		// for (String path : parts) {
		// FilePath src = workspace.child(path);
		// if (src.exists()) {
		// if (src.isDirectory()) {
		// files.addAll(Arrays.asList(src.list("**/*")));
		// } else {
		// files.add(src);
		// }
		// }
		// }
		// return files;
	}

	public static List<File> copyToBuildDir(AbstractBuild<?, ?> build,
			List<FilePath> files) throws IOException, InterruptedException {
		List<File> localFiles = new ArrayList<File>();
		for (FilePath src : files) {
			if (src.isDirectory()) {
				LOGGER.warning("copyToMaster(): File '" + src.getName()
						+ "' is a directory, not a Performance Report");
				continue;
			}
			File localFile = new File(build.getRootDir(),
					Constants.INPUT_DIR_RELATIVE_PATH + File.separator
							+ src.getName());
			if (localFile.exists()) {
				LOGGER.warning("copyToMaster(): File '"
						+ src.getName()
						+ "' has a duplicated file name, will replace the old one.");
			}
			src.copyTo(new FilePath(localFile));
			localFiles.add(localFile);
		}
		return localFiles;
	}

}
