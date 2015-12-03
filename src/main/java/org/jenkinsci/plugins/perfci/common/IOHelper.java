package org.jenkinsci.plugins.perfci.common;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by vfreex on 11/26/15.
 */
public class IOHelper {
//    public static List<FilePath> locateFiles(FilePath basePath, String includes)
//            throws IOException, InterruptedException {
//        String parts[] = includes.split("\\s*[;:,]+\\s*");
//        // Use map to discard duplicated matched files
//        Map<String, FilePath> fileMap = new HashMap<String, FilePath>();
//        for (String path : parts) {
//            for (FilePath filePath : basePath.list(path)) {
//                fileMap.put(filePath.getRemote(), filePath);
//            }
//        }
//        List<FilePath> files = new ArrayList<>(fileMap.size());
//        for (String fileName : fileMap.keySet()) {
//            files.add(fileMap.get(fileName));
//        }
//        return files;
//    }

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
    public static void copyFilesFromWorkspace(List<FilePath> files, String toDir, AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        String localFilePathString = build.getRootDir().getAbsolutePath() + File.separator + toDir;
        FilePath localFilePath = new FilePath(new File(localFilePathString));
        for (FilePath src : files) {
            if (src.isDirectory()) {
                listener.getLogger().println("[WARNING] File '" + src.getName()
                        + "' is a directory. We won't copy directories.");
                continue;
            }
            FilePath zippedFile = new FilePath(new File(localFilePathString + File.separator + src.getName() + ".zip"));
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
                } catch (Exception ex) {
                    if (_try > 5) {
                        throw new IOException("Failed to copy file '" + src.getName() + "'. Give up.", ex);
                    }
                    listener.getLogger().println("[WARNING] Copy '" + src.getName() + "' failed. Try again in " + _try + " minute.\n" + ex.toString());
                    Thread.sleep(1000 * 60 * _try);
                }
            }
        }
    }

    public static String relativizePath(FilePath base, FilePath path) {
        return new File(base.getRemote()).toURI().relativize(new File(path.getRemote()).toURI()).getPath();
    }

    public static void copyDirFromWorkspace(FilePath src, String pathToBuildDir, AbstractBuild<?, ?> build, BuildListener listener) throws IOException, InterruptedException {
        if (!src.exists() || !src.isDirectory())
            throw new IOException("Directory '" + src.getName() +
                    "' does not exist.");
        URI srcRelativeToWorkspace = new File(build.getWorkspace().getRemote()).toURI().relativize(new File(src.getRemote()).toURI());
        if (srcRelativeToWorkspace.isAbsolute())
            throw new IOException("FilePath `src` is not located in workspace.");
        FilePath pathOnMaster = new FilePath(new File(build.getRootDir().getAbsolutePath() + File.separator + pathToBuildDir));
        FilePath zippedFile = pathOnMaster.getParent().child(src.getName() + ".zip");
        int _try;
        for (_try = 1; _try <= 5; ++_try) {
            try {
                listener.getLogger().println("[INFO] Zipping and copying file '" + src.getName() + "' to master...");
                src.zip(zippedFile);
                listener.getLogger().println("[INFO] Unzipping file '" + src.getName() + "' on master...");
                zippedFile.unzip(pathOnMaster);
                zippedFile.delete();
                listener.getLogger().println("[INFO] File '" + src.getName() + "' has been copied to build directory on master.");
                break;
            } catch (Exception ex) {
                if (_try > 5) {
                    throw new IOException("Failed to copy file '" + src.getName() + "'. Give up.", ex);
                }
                listener.getLogger().println("[WARNING] Copy '" + src.getName() + "' failed. Try again in " + _try + " minute.\n" + ex.toString());
                Thread.sleep(1000 * 60 * _try);
            }
        }
    }
}
