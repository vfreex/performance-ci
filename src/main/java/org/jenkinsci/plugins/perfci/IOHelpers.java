package org.jenkinsci.plugins.perfci;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

import java.io.*;
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
        String parts[] = includes.split("\\s*[;:,]+\\s*");
        List<FilePath> files = new ArrayList<FilePath>();
        for (String path : parts) {
            FilePath[] ret = workspace.list(path);
            if (ret.length > 0) {
                files.addAll(Arrays.asList(ret));
            }
        }
        return files;
    }

    public static void copyToBuildDir(AbstractBuild<?, ?> build,
                                      List<FilePath> files, BuildListener listener) throws IOException, InterruptedException {
        String localFilePathString = IOHelpers.concatPathParts(build.getRootDir().getAbsolutePath(), Constants.INPUT_DIR_RELATIVE_PATH);
        FilePath localFilePath = new FilePath(new File(localFilePathString));
        for (FilePath src : files) {
            if (src.isDirectory()) {
                listener.getLogger().println("[WARNING] File '" + src.getName()
                        + "' is a directory. We won't copy directories.");
                continue;
            }
            FilePath zippedFile = new FilePath(new File(IOHelpers.concatPathParts(localFilePathString, src.getName() + ".zip")));
            int _try;
            for (_try = 1; _try <= 5; ++_try) {
                try {
                    listener.getLogger().println("[INFO] Zipping and copying file '" + src.getName() + "' to master...");
                    src.zip(zippedFile);
                    listener.getLogger().println("[INFO] Unzipping file '" + src.getName() + "' on master...");
                    zippedFile.unzip(localFilePath);
                    zippedFile.delete();
                    listener.getLogger().println("[INFO] File '" + src.getName() + "' has been copied to build directory on master.");
                    break;
                } catch (IOException ex) {
                    listener.getLogger().println("[WARNING] Copy '" + src.getName() + "' failed. Try again in " + _try + " minute.\n" + ex.toString());
                    Thread.sleep(1000 * 60 * _try);
                } catch (InterruptedException ex) {
                    listener.getLogger().println("[WARNING] Copy '" + src.getName() + "' failed. Try again in " + _try + " minute.\n" + ex.toString());
                    Thread.sleep(1000 * 60 * _try);
                }
            }
            if (_try > 5) {
                throw new IOException("Copy file '" + src.getName() + "' failed.");
            }
        }
    }

    /*public static void copyToBuildDirectoryFromWorkspace(AbstractBuild<?, ?> build, final String glob) throws IOException, InterruptedException {
        final FilePath workspace = build.getWorkspace();
        FilePath remoteTarFile = workspace.createTempFile("temp", "tar");
        LOGGER.info("archiving file...");
        if (0 != remoteTarFile.act(new FilePath.FileCallable<Integer>() {
            private static final long serialVersionUID = 1;
            @Override
            public Integer invoke(File f, VirtualChannel channel) {
                try {
                    workspace.tar(new FileOutputStream(f), new DirScanner() {
                        @Override
                        public void scan(File file, FileVisitor fileVisitor) throws IOException {
                        }
                    });
                    workspace.tar(new FileOutputStream(f), glob);
                } catch (IOException e) {
                    e.printStackTrace();
                    return 1;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return 2;
                }
                return 0;
            }
            @Override
            public void checkRoles(RoleChecker var1) throws SecurityException {
            }
        })) {
            throw new IOException("cannot create archive file in workspace");
        }
        LOGGER.info("zipping and copying file to master...");
        FilePath zippedFile = new FilePath(new File(build.getRootDir(),
                Constants.INPUT_DIR_RELATIVE_PATH + File.separator
                        + "temp.tar.gz"));
        remoteTarFile.zip(zippedFile);
        FilePath localPath = new FilePath(new File(build.getRootDir(),
                Constants.INPUT_DIR_RELATIVE_PATH + File.separator));
        LOGGER.info("unzipping...");
        zippedFile.untar(localPath, FilePath.TarCompression.GZIP);
        LOGGER.info("all files has been copied to master");
    }*/

    public static void cleanInputPath(String inputPath) {
        File inputDir = new File(inputPath);
        if (inputDir.isDirectory()) {
            File[] children = inputDir.listFiles();
            for (int i = 0; i < children.length; i++) {
                if (children[i].isFile())
                    children[i].delete();

            }
        }
    }


}
