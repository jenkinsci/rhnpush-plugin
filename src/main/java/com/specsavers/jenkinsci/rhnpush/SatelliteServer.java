package com.specsavers.jenkinsci.rhnpush;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

public class SatelliteServer extends AbstractDescribableImpl<SatelliteServer> {
  private final String hostname;
  private final String username;
  private final Secret password;

  @DataBoundConstructor
  public SatelliteServer(String hostname, String username, Secret password) {
    this.hostname = hostname;
    this.username = username;
    this.password = password;
  }

  public String getHostname() {
    return hostname;
  }

  public String getUsername() {
    return username;
  }

  public Secret getPassword() {
    return password;
  }

  @Extension
  public static class DescriptorImpl extends Descriptor<SatelliteServer> {
    @Override
    public String getDisplayName() {
      return ""; // unused
    }
  }
}
