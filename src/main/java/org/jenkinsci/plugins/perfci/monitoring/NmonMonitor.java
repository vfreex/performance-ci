package org.jenkinsci.plugins.perfci.monitoring;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.remoting.Callable;
import hudson.tasks.Builder;
import hudson.util.FormValidation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;
import jenkins.security.Roles;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.xfer.InMemorySourceFile;

import org.jenkinsci.remoting.RoleChecker;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked and a new
 * {@link NmonMonitor} is created. The created instance is persisted to the
 * project configuration XML by using XStream, so this allows you to use
 * instance fields (like {@link #name}) to remember the configuration.
 *
 * <p>
 * When a build is performed, the
 * {@link #perform(AbstractBuild, Launcher, BuildListener)} method will be
 * invoked.
 *
 * @author Kohsuke Kawaguchi, Rayson Zhu
 */
public class NmonMonitor implements ResourceMonitor,
		Describable<ResourceMonitor>, ExtensionPoint {
	@Extension
	public static class DescriptorImpl extends ResourceMonitorDescriptor {

		@Override
		public String getDisplayName() {
			return "NMON Monitor";
		}

		public FormValidation doCheckHost(@QueryParameter String host) {
			if (host == null || host.isEmpty()) {
				return FormValidation.error("Host name can't be empty");
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckName(@QueryParameter String name) {
			if (name == null || name.isEmpty()) {
				return FormValidation.error("User name can't be empty");
			}
			return FormValidation.ok();
		}

		// public FormValidation doCheckPassword(@QueryParameter String
		// password) {
		// if (password == null || password.isEmpty()) {
		// return FormValidation.error("Password can't be empty");
		// }
		// return FormValidation.ok();
		// }

		public FormValidation doCheckFingerprint(
				@QueryParameter String fingerprint) {
			if (fingerprint == null || fingerprint.isEmpty()) {
				return FormValidation.ok();
			}
			if (!Pattern.matches("[\\da-fA-F]{1,2}(?:\\:[\\da-fA-F]{1,2}){15}",
					fingerprint)) {
				return FormValidation.error("Invalid fingerprint format");
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckInterval(@QueryParameter String interval) {
			if (interval == null || interval.isEmpty()) {
				return FormValidation.error("Interval can't be empty");
			}
			if (!Pattern.matches("\\d{1,10}", interval)) {
				return FormValidation.error("Invalid interval format");
			}
			if (Integer.parseInt(interval) <= 0) {
				return FormValidation.error("Invalid interval range");
			}
			return FormValidation.ok();
		}
	}

	private static final Logger LOGGER = Logger.getLogger(NmonMonitor.class
			.getName());

	private final String host;
	private final String name;
	private final String password;
	private final String interval;
	private final String outputPath;
	private final String fingerprint;
	// private Object monfile;
	private final static int TIMEOUT = 30000;
	private final static int MAX_TRIES = 5;

	// Fields in config.jelly must match the parameter names in the
	// "DataBoundConstructor"
	@DataBoundConstructor
	public NmonMonitor(String host, String name, String password,
			String interval, String outputPath, String fingerprint) {
		this.host = host;
		this.name = name;
		this.password = password;
		this.interval = interval;
		this.outputPath = outputPath;
		this.fingerprint = fingerprint;
	}

	/**
	 * We'll use this from the <tt>config.jelly</tt>.
	 */
	public String getName() {
		return name;
	}

	public String getPassword() {
		return password;
	}

	public String getHost() {
		return host;
	}

	public String getInterval() {
		return interval;
	}

	@Override
	public boolean start(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws Exception {
		return start(build.getProject().getName(), build.getId(), build
				.getWorkspace().getRemote(), listener);
	}

	private boolean tryStart(String projectName, String buildID,
			String workspace, BuildListener listener) throws IOException {
		String projectDir = getProjectDir(projectName);
		SSHClient client = new SSHClient();
		if (fingerprint != null && !fingerprint.isEmpty())
			client.addHostKeyVerifier(fingerprint);
		else
			client.addHostKeyVerifier(new PromiscuousVerifier());
		try {
			listener.getLogger().println(
					"INFO: Connecting to host \"" + host + "\"...");
			client.connect(host);
			listener.getLogger().println("INFO: Authenticating...");
			if (password == null || password.isEmpty())
				client.authPublickey(name);
			else
				client.authPassword(name, password);
			Session.Command cmd;
			Session session = client.startSession();
			listener.getLogger().println("INFO: Checking NMON existence.");
			cmd = session.exec("ls -lrt /tmp/jenkins-perfci/bin/nmon");
			cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
			session.close();
			if (cmd.getExitStatus() == 2) {
				listener.getLogger().println(
						"INFO: NMON does not exist. Uploading...");
				session = client.startSession();
				cmd = session.exec("mkdir -p /tmp/jenkins-perfci/bin");
				cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
				session.close();
				if (cmd.getExitStatus() != 0)
					return false;
				final URL nmonfile = getClass()
						.getResource(
								"/org/jenkinsci/plugins/perfci/monitoring/NmonMonitor/nmon");
				final InputStream in = nmonfile.openStream();
				final ByteArrayOutputStream out = new ByteArrayOutputStream();
				org.apache.commons.io.IOUtils.copy(in, out);
				in.close();
				final byte[] bytes = out.toByteArray();
				out.close();
				client.newSCPFileTransfer().upload(new InMemorySourceFile() {
					public String getName() {
						return "nmon";
					}

					public long getLength() {
						return bytes.length;
					}

					public InputStream getInputStream() throws IOException {
						return new ByteArrayInputStream(bytes);
					}
				}, "/tmp/jenkins-perfci/bin/");
				listener.getLogger().println(
						"INFO: NMON has been uploaded to target host.");
			}
			listener.getLogger().println("INFO: Starting NMON deamon...");
			String remoteLogDir = getOutputDir(projectName, buildID);
			session = client.startSession();
			cmd = session
					.exec("mkdir -p '"
							+ remoteLogDir
							+ "' && chmod +x /tmp/jenkins-perfci/bin/nmon && /tmp/jenkins-perfci/bin/nmon -f -t -s"
							+ Integer.parseInt(interval) + " -c64080 -m "
							+ remoteLogDir);
			cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
			session.close();
			if (cmd.getExitStatus() != 0)
				return false;
			listener.getLogger().println(
					"INFO: Checking whether NMON is running...");
			session = client.startSession();
			cmd = session
					.exec("sleep 3 && ps -ef | grep /tmp/jenkins-perfci/bin/[n]mon | grep '"
							+ projectDir + "' | awk '{print $2}'");
			cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
			session.close();
			if (cmd.getExitStatus() != 0)
				return false;
			if (IOUtils.readFully(cmd.getInputStream()).toString().trim()
					.isEmpty()) {
				return false;
			}
			return true;
		} catch (UserAuthException ex) {
			throw ex;
		} catch (TransportException ex) {
			throw ex;
		} catch (IOException ex) {
			return false;
		} finally {
			client.close();
		}
	}

	private static String getOutputDir(String projectName, String buildID) {
		return getProjectDir(projectName) + "/" + buildID + "/monitoring";
	}

	private static String getProjectDir(String projectName) {
		return "/tmp/jenkins-perfci/jobs/" + projectName;
	}

	@Override
	public boolean stop(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws Exception {
		return stop(build.getProject().getName(), build.getId(), build
				.getWorkspace().getRemote(), listener);
	}

	private boolean tryStop(String projectName, String buildID,
			String workspace, BuildListener listener) throws IOException {
		String projectDir = getProjectDir(projectName);
		SSHClient client = new SSHClient();
		if (fingerprint != null && !fingerprint.isEmpty())
			client.addHostKeyVerifier(fingerprint);
		else
			client.addHostKeyVerifier(new PromiscuousVerifier());
		try {
			listener.getLogger().println(
					"INFO: Connecting to host \"" + host + "\"...");
			client.connect(host);
			listener.getLogger().println("INFO: Authenticating...");
			if (password == null || password.isEmpty())
				client.authPublickey(name);
			else
				client.authPassword(name, password);
			listener.getLogger().println("INFO: Try killing NMON deamon...");
			Session.Command cmd;
			Session session = client.startSession();
			cmd = session
					.exec("sync && kill `ps -ef | grep /tmp/jenkins-perfci/bin/[n]mon | grep '"
							+ projectDir + "' | awk '{print $2}'`");
			cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
			session.close();
			if (cmd.getExitStatus() != 0 && cmd.getExitStatus() != 1)
				return false;
			session = client.startSession();
			cmd = session
					.exec("sleep 3 && ps -ef | grep /tmp/jenkins-perfci/bin/[n]mon | grep '"
							+ projectDir + "' | awk '{print $2}'");
			cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
			session.close();
			if (cmd.getExitStatus() != 0)
				return false;
			if (!IOUtils.readFully(cmd.getInputStream()).toString().trim()
					.isEmpty()) {
				LOGGER.info("Cannot stop, try 'kill -s INT'...");
				listener.getLogger().println(
						"INFO: Cannot stop, try 'kill -s INT'...");
				session = client.startSession();
				cmd = session
						.exec("sync && kill -s INT `ps -ef | grep /tmp/jenkins-perfci/bin/[n]mon | grep '"
								+ projectDir + "' | awk '{print $2}'`");
				cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
				session.close();
				if (cmd.getExitStatus() != 0 && cmd.getExitStatus() != 1)
					return false;
				session = client.startSession();
				cmd = session
						.exec("sleep 5 && ps -ef | grep /tmp/jenkins-perfci/bin/[n]mon | grep '"
								+ projectDir + "' | awk '{print $2}'");
				cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
				session.close();
				if (cmd.getExitStatus() != 0)
					return false;
				if (!IOUtils.readFully(cmd.getInputStream()).toString().trim()
						.isEmpty()) {
					LOGGER.info("Cannot stop, try 'kill -s KILL'...");
					listener.getLogger().println(
							"INFO: Cannot stop, try 'kill -s KILL'...");
					session = client.startSession();
					cmd = session
							.exec("sync && kill -s KILL `ps -ef | grep /tmp/jenkins-perfci/bin/[n]mon | grep '"
									+ projectDir + "' | awk '{print $2}'`");
					cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
					session.close();
					if (cmd.getExitStatus() != 0 && cmd.getExitStatus() != 1)
						return false;
					session = client.startSession();
					cmd = session
							.exec("sleep 5 && ps -ef | grep /tmp/jenkins-perfci/bin/[n]mon | grep '"
									+ projectDir + "' | awk '{print $2}'");
					cmd.join(TIMEOUT, TimeUnit.MILLISECONDS);
					session.close();
					if (cmd.getExitStatus() != 0)
						return false;
					if (!IOUtils.readFully(cmd.getInputStream()).toString()
							.trim().isEmpty()) {
						LOGGER.info("Oops! Still cannot stop. That was strange. Give up this try.");
						listener.getLogger()
								.println(
										"WARNING: Still cannot stop. That was strange. Give up this try.");
						return false;
					}
				}
			}

			String relativePathForNMONFiles = outputPath == null
					|| outputPath.isEmpty() ? "monitoring" : outputPath;
			String pathOnAgent = relativePathForNMONFiles.startsWith("/")
					|| relativePathForNMONFiles.startsWith("file:") ? relativePathForNMONFiles
					: workspace + File.separator + relativePathForNMONFiles;
			LOGGER.info("Copy NMON logs to Jenkins agent '" + pathOnAgent
					+ "'...");
			listener.getLogger().println(
					"INFO: Copy NMON logs to Jenkins agent '" + pathOnAgent
							+ "'...");
			new File(pathOnAgent).mkdirs();
			String from = getOutputDir(projectName, buildID);
			String to = pathOnAgent;
			LOGGER.info("'monitored:" + from + "' ==> 'agent:" + to + "'...");
			listener.getLogger().println(
					"INFO: 'monitored:" + from + "' ==> 'agent:" + to + "'...");

			client.newSCPFileTransfer().download(from, to);
			LOGGER.info("'monitored:" + from + "' ==> 'agent:" + to + "' done");
			listener.getLogger().println(
					"INFO: 'monitored:" + from + "' ==> 'agent:" + to
							+ "' done");
			return true;
		} catch (UserAuthException ex) {
			throw ex;
		} catch (IOException ex) {
			return false;
		} finally {
			client.close();
		}
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) Jenkins.getInstance().getDescriptor(
				NmonMonitor.class);
	}

	public String getOutputPath() {
		return outputPath;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	@Override
	public boolean start(String projectName, String buildID, String workspace,
			BuildListener listener) throws Exception {
		try {
			for (int i = 1; i <= MAX_TRIES; ++i) {
				LOGGER.info("Starting NMON monitor... (try " + i + " of "
						+ MAX_TRIES + ")");
				listener.getLogger().println(
						"INFO: Starting NMON monitor... (try " + i + " of "
								+ MAX_TRIES + ")");
				if (tryStart(projectName, buildID, workspace, listener)) {
					LOGGER.info("INFO: NMON monitor started.");
					listener.getLogger().println("INFO: NMON monitor started.");
					return true;
				}
				LOGGER.warning("Fail to start NMON monitor.");
				listener.getLogger().println(
						"WARNING: Fail to start NMON monitor.");
			}
		} catch (Exception ex) {
			LOGGER.warning("Fail to start NMON monitor: " + ex.toString());
			listener.getLogger().println(
					"ERROR: Fail to start NMON monitor: " + ex.toString());
		}
		LOGGER.warning("Cannot start NMON monitor. Give up.");
		listener.getLogger().println(
				"WARNING: Cannot start NMON monitor. Give up.");
		return false;
	}

	@Override
	public boolean stop(String projectName, String buildID, String workspace,
			BuildListener listener) throws Exception {
		try {
			for (int i = 1; i <= MAX_TRIES; ++i) {
				LOGGER.info("Stopping NMON monitors... (try " + i + " of "
						+ MAX_TRIES + ")");
				if (tryStop(projectName, buildID, workspace, listener)) {
					LOGGER.info("NMON monitors stopped.");
					return true;
				}
				LOGGER.warning("Fail to stop NMON monitors.");
			}
		} catch (UserAuthException ex) {
			LOGGER.warning("Authentication error.");
			listener.getLogger().println("ERROR: Authentication error.");
		}
		LOGGER.warning("Cannot stop NMON monitors. Give up.");
		return false;
	}

	@Override
	public void checkRoles(RoleChecker checker, Callable<?, ? extends SecurityException> callable)
			throws SecurityException {
		checker.check(callable, Roles.SLAVE, Roles.MASTER);
	}


}
