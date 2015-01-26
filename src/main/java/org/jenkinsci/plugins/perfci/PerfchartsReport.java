package org.jenkinsci.plugins.perfci;

import hudson.model.AbstractProject;

public abstract class PerfchartsReport {
	public final AbstractProject<?, ?> project;

	public PerfchartsReport(AbstractProject<?, ?> project) {
		this.project = project;
	}

	public abstract String getDataJSPath();
}
