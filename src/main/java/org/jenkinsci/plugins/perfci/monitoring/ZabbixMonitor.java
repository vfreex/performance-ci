package org.jenkinsci.plugins.perfci.monitoring;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.AbstractBuild;
import hudson.model.Descriptor;
import hudson.util.FormValidation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import jenkins.model.Jenkins;

import org.jenkinsci.plugins.perfci.Constants;
import org.jenkinsci.plugins.perfci.PerfchartsRecorder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONWriter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class ZabbixMonitor implements ResourceMonitor,
		Describable<ResourceMonitor>, ExtensionPoint {

	@Extension
	public static class DescriptorImpl extends ResourceMonitorDescriptor {

		@Override
		public String getDisplayName() {
			return "Zabbix Monitor";
		}

		public FormValidation doCheckApiUrl(@QueryParameter String apiUrl) {
			if (apiUrl == null || apiUrl.isEmpty()) {
				return FormValidation
						.error("Zabbix API URL list can't be empty");
			}
			if (!Pattern.matches("https?://\\S+", apiUrl)) {
				return FormValidation.error("Invalid Zabbix API URL format");
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckUserName(@QueryParameter String userName) {
			if (userName == null || userName.isEmpty()) {
				return FormValidation.error("Zabbix user name can't be empty");
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckPassword(@QueryParameter String password) {
			if (password == null || password.isEmpty()) {
				return FormValidation.error("Zabbix password can't be empty");
			}
			return FormValidation.ok();
		}

		public FormValidation doCheckHosts(@QueryParameter String hosts) {
			if (hosts == null || hosts.isEmpty()) {
				return FormValidation.error("Host list can't be empty");
			}
			if (!Pattern.matches("[\\w\\-\\. ]+(?:;[\\w\\-\\. ]+)*", hosts)) {
				return FormValidation.error("Invalid host list format");
			}
			return FormValidation.ok();
		}
	}

	private static final Logger LOGGER = Logger.getLogger(ZabbixMonitor.class
			.getName());

	private final String apiUrl;
	private final String userName;
	private final String password;
	private final String hosts;
	private final boolean autoStart;
	private final boolean autoStop;
	private final String outputPath;
	private final static int TIMEOUT = 30000;
	private final static int MAX_TRIES = 5;
	private Date startTime;
	private Date endTime;
	private int sequenceId = 0;

	@DataBoundConstructor
	public ZabbixMonitor(String apiUrl, String userName, String password,
			String hosts, boolean autoStart, boolean autoStop, String outputPath) {
		this.apiUrl = apiUrl;
		this.userName = userName;
		this.password = password;
		this.hosts = hosts;
		this.autoStart = autoStart;
		this.autoStop = autoStop;
		this.outputPath = outputPath;
	}

	public String getApiUrl() {
		return apiUrl;
	}

	public String getUserName() {
		return userName;
	}

	public String getPassword() {
		return password;
	}

	public String getHosts() {
		return hosts;
	}

	public boolean getAutoStart() {
		return autoStart;
	}

	public boolean getAutoStop() {
		return autoStop;
	}

	public String getOutputPath() {
		return outputPath;
	}

	@Override
	public Descriptor<ResourceMonitor> getDescriptor() {
		return (DescriptorImpl) Jenkins.getInstance().getDescriptor(
				ZabbixMonitor.class);
	}

	@Override
	public boolean start(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws Exception {
		String apiVersion = getAPIVersion();
		listener.getLogger().println("INFO: Zabbix API version: " + apiVersion);
		String auth = null;
		listener.getLogger().println("INFO: Logging in...");
		try {
			auth = authenticate();
		} catch (Exception ex) {
			listener.getLogger().println(
					"ERROR: Authentication Failed." + ex.toString());
			return false;
		}
		if (autoStart) {
			try {
				for (int i = 1; i <= MAX_TRIES; i++) {
					LOGGER.info("Starting Zabbix monitor... (try " + i + " of "
							+ MAX_TRIES + ")");
					listener.getLogger().println(
							"INFO: Starting Zabbix monitor... (try " + i
									+ " of " + MAX_TRIES + ")");
					if (tryStart(build, listener, auth)) {
						logout(auth);
						LOGGER.info("INFO: Zabbix monitor started.");
						listener.getLogger().println(
								"INFO: Zabbix monitor started.");
						startTime = new Date();
						return true;
					}
				}
			} catch (Exception ex) {
				LOGGER.warning("Fail to start Zabbix monitor: " + ex.toString());
				listener.getLogger()
						.println(
								"ERROR: Fail to start Zabbix monitor: "
										+ ex.toString());
			}
			LOGGER.warning("Cannot start Zabbix monitor. Give up.");
			listener.getLogger().println(
					"WARNING: Cannot start Zabbix monitor. Give up.");
			logout(auth);
			return false;
		}
		logout(auth);
		startTime = new Date();
		return true;
	}

	public boolean tryStart(AbstractBuild<?, ?> build, BuildListener listener,
			String auth) throws Exception {
		listener.getLogger().println(
				"INFO: Geting host IDs for \"" + hosts + "\"...");
		Collection<Integer> hostIDs = getHostIDs(auth);
		if (hostIDs.isEmpty()) {
			listener.getLogger().println("WARNING: No valid hosts found.");
			throw new InvalidParameterException("No valid hosts found.");
		}
		try {
			listener.getLogger().println("INFO: Setting host status to 0...");
			setHostStatus(hostIDs, 0, auth);
			listener.getLogger()
					.println("INFO: Start monitoring successfully.");
			return true;
		} catch (Exception e) {
			listener.getLogger().println("WARNING: Cannot start monitoring.");
			listener.getLogger().println(e.toString());
			return false;
		}
	}

	public boolean tryStop(AbstractBuild<?, ?> build, BuildListener listener,
			String auth) throws Exception {
		listener.getLogger().println(
				"INFO: Geting host IDs for \"" + hosts + "\"...");
		Collection<Integer> hostIDs = getHostIDs(auth);
		if (hostIDs.isEmpty()) {
			listener.getLogger().println("WARNING: No valid hosts found.");
			throw new InvalidParameterException("No valid hosts found.");
		}
		try {
			listener.getLogger().println("INFO: Setting host status to 1...");
			setHostStatus(hostIDs, 1, auth);
			listener.getLogger().println("INFO: Stop monitoring successfully.");
			return true;
		} catch (Exception e) {
			listener.getLogger().println("WARNING: Cannot stop monitoring.");
			listener.getLogger().println(e.toString());
			return false;
		}
	}

	@Override
	public boolean stop(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws Exception {
		String apiVersion = getAPIVersion();
		listener.getLogger().println("INFO: Zabbix API version: " + apiVersion);
		String auth = null;
		listener.getLogger().println("INFO: Logging in...");
		try {
			auth = authenticate();
		} catch (Exception ex) {
			listener.getLogger().println(
					"ERROR: Authentication Failed." + ex.toString());
			return false;
		}
		if (autoStop) {
			try {
				for (int i = 1; i <= MAX_TRIES; i++) {
					LOGGER.info("Stopping Zabbix monitor... (try " + i + " of "
							+ MAX_TRIES + ")");
					listener.getLogger().println(
							"INFO: Stopping Zabbix monitor... (try " + i
									+ " of " + MAX_TRIES + ")");
					if (tryStop(build, listener, auth)) {
						logout(auth);
						endTime = new Date();
						LOGGER.info("INFO: Zabbix monitor stopped.");
						listener.getLogger().println(
								"INFO: Zabbix monitor stopped.");
						if (!downloadData(build, listener)) {
							listener.getLogger()
									.println(
											"ERROR: Fail to download data from Zabbix server.");
							return false;
						}
						return true;
					}
				}
			} catch (Exception ex) {
				LOGGER.warning("Fail to stop Zabbix monitor: " + ex.toString());
				listener.getLogger().println(
						"ERROR: Fail to stop Zabbix monitor: " + ex.toString());
			}
			LOGGER.warning("Cannot stop Zabbix monitor. Give up.");
			listener.getLogger().println(
					"WARNING: Cannot stop Zabbix monitor. Give up.");
			logout(auth);
			return false;
		}
		logout(auth);
		endTime = new Date();
		if (!downloadData(build, listener)) {
			listener.getLogger().println(
					"ERROR: Fail to download data from Zabbix server.");
			return false;
		}
		return true;
	}

	private boolean downloadData(AbstractBuild<?, ?> build,
			BuildListener listener) throws IOException, InterruptedException {
		PerfchartsRecorder.DescriptorImpl desc = (PerfchartsRecorder.DescriptorImpl) Jenkins
				.getInstance().getDescriptor(PerfchartsRecorder.class);
		String cgtHome = desc.getCgtHome();
		if (cgtHome == null) {
			throw new RuntimeException("$CGT_HOME is not set for Perfcharts.");
		}
		List<String> arguments = new ArrayList<String>();
		arguments.add(cgtHome + File.separator
				+ Constants.CGT_ZABBIX_DL);

		String relativePath = outputPath == null || outputPath.isEmpty() ? "monitoring"
				: outputPath;
		String pathOnAgent = relativePath.startsWith("/")
				|| relativePath.startsWith("file:") ? relativePath : build
				.getWorkspace().getRemote() + File.separator + relativePath;
		LOGGER.info("Copy NMON logs to Jenkins agent '" + pathOnAgent + "'...");
		listener.getLogger().println(
				"INFO: Copy NMON logs to Jenkins agent '" + pathOnAgent
						+ "'...");
		new File(pathOnAgent).mkdirs();

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-M-d h:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		arguments.add("-a");
		arguments.add(apiUrl);
		arguments.add("-u");
		arguments.add(userName);
		arguments.add("-h");
		arguments.add(hosts);
		arguments.add("-z");
		arguments.add("GMT");
		arguments.add("-f");
		arguments.add(sdf.format(startTime));
		arguments.add("-f");
		arguments.add(sdf.format(endTime));

		arguments.add(pathOnAgent);
		ProcessBuilder cgtProcessBuilder = new ProcessBuilder(arguments);
		listener.getLogger().println(
				"INFO: Downloading monitoring data from Zabbix server '"
						+ apiUrl + "'...");
		Process cgtProcess = cgtProcessBuilder.start();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				cgtProcess.getOutputStream()));
		writer.write(password);
		writer.flush();
		writer.close();
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				cgtProcess.getErrorStream()));
		String line;
		while ((line = reader.readLine()) != null) {
			listener.getLogger().println(line);
		}
		int returnCode = cgtProcess.waitFor();
		boolean success = returnCode == 0;
		return success;
	}

	@Override
	public void collect(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isRuning(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) {
		// TODO Auto-generated method stub
		return false;
	}

	private void setHostStatus(Collection<Integer> hostIDs, int status,
			String auth) throws MalformedURLException, JSONException,
			IOException, ZabbixAPIException {
		JSONArray param = new JSONArray();
		for (Integer integer : hostIDs) {
			param.put(new JSONObject().put("hostid", integer).put("status",
					status));
		}

		JSONObject retObj = callRPC(apiUrl, "host.update", param, auth,
				++sequenceId);
		throwIfFailed(retObj);
	}

	private Collection<Integer> getHostIDs(String auth)
			throws MalformedURLException, JSONException, IOException,
			ZabbixAPIException {
		// get host IDs
		LOGGER.info("Fetching information of monitored hosts...");
		if (hosts == null || hosts.isEmpty())
			return new ArrayList<Integer>();
		String[] hostArr = hosts.split(";");
		JSONObject retObj = callRPC(
				apiUrl,
				"host.get",
				new JSONObject().put("filter",
						new JSONObject().put("host", new JSONArray(hostArr)))
						.put("output", "extend"), auth, ++sequenceId);
		throwIfFailed(retObj);
		JSONArray arr = retObj.getJSONArray("result");
		Collection<Integer> r = new ArrayList<Integer>(arr.length());
		for (int i = 0; i < arr.length(); i++) {
			JSONObject host = arr.getJSONObject(i);
			int hostId = host.getInt("hostid");
			r.add(hostId);
		}
		return r;
	}

	private void logout(String auth) throws MalformedURLException, IOException,
			ZabbixAPIException {
		LOGGER.info("Logging out...");
		callRPC(apiUrl, "user.logout", null, auth, ++sequenceId);
		LOGGER.info("Done.");
	}

	private String authenticate() throws MalformedURLException, JSONException,
			IOException, ZabbixAPIException {
		LOGGER.info("Authenticating...");
		JSONObject retObj = callRPC(apiUrl, "user.authenticate",
				new JSONObject().put("user", userName)
						.put("password", password), null, ++sequenceId);
		throwIfFailed(retObj);
		final String auth = retObj.getString("result");
		return auth;
	}

	private String getAPIVersion() throws MalformedURLException, IOException,
			ZabbixAPIException {
		LOGGER.info("Get API version of target Zabbix server...");
		JSONObject retObj = callRPC(apiUrl, "apiinfo.version", null, null,
				++sequenceId);
		throwIfFailed(retObj);
		String apiVersion = retObj.getString("result");
		return apiVersion;
	}

	private static void throwIfFailed(JSONObject retObj) throws IOException,
			ZabbixAPIFailureException {
		if (!retObj.has("error"))
			return;
		JSONObject error = retObj.getJSONObject("error");
		if (error == null)
			return;
		int code = error.getInt("code");
		if (code == 0)
			return;
		throw new ZabbixAPIFailureException(code, error.getString("message"),
				error.getString("data"));
	}

	private static JSONObject callRPC(String url, String method, Object params,
			String auth, int id) throws MalformedURLException, IOException,
			ZabbixAPIException {
		LOGGER.info("JSON RPC Request begins: " + method);
		HttpURLConnection http = (HttpURLConnection) new URL(url)
				.openConnection();
		http.setDoOutput(true);
		http.setDoInput(true);
		http.setUseCaches(false);
		http.setConnectTimeout(TIMEOUT);
		http.setRequestMethod("POST");
		http.setRequestProperty("Connection", "Close");
		http.setRequestProperty("Content-Type", "application/json-rpc");
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
				http.getOutputStream()));
		JSONWriter jsonWriter = new JSONWriter(writer);
		jsonWriter.object().key("jsonrpc").value("2.0").key("method")
				.value(method).key("id").value(id).key("auth").value(auth)
				.key("params").value(params).endObject();
		writer.flush();
		JSONObject r = new JSONObject(new JSONTokener(new BufferedReader(
				new InputStreamReader(http.getInputStream()))));
		http.disconnect();
		LOGGER.info("JSON RPC Request ends: " + method);
		return r;
	}

	public Date getStartTime() {
		return startTime;
	}

	public void setStartTime(Date startTime) {
		this.startTime = startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public void setEndTime(Date endTime) {
		this.endTime = endTime;
	}

	public static class ZabbixAPIException extends Exception {
		/**
		 * 
		 */
		private static final long serialVersionUID = -5315732547492948417L;

		public ZabbixAPIException() {
		}

		public ZabbixAPIException(String message) {
			super(message);
		}
	}

	public static class ZabbixAPIFailureException extends ZabbixAPIException {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1063340990737841665L;
		private final int code;
		private final String data;
		private final String shortMessage;

		public ZabbixAPIFailureException() {
			this.shortMessage = "";
			this.code = 0;
			this.data = "";
		}

		public ZabbixAPIFailureException(int code, String message, String data) {
			super("Zabbix API error " + code + ". " + message + " " + data);
			this.shortMessage = message;
			this.code = code;
			this.data = data;
		}

		public int getCode() {
			return code;
		}

		public String getData() {
			return data;
		}

		public String getShortMessage() {
			return shortMessage;
		}
	}

}
