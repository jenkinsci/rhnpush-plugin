package jenkins.plugins.rhnpush;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

public class RhnPush extends Recorder {

  private final boolean newest;
  private List<RhnPushEntry> entries = Collections.emptyList();
  private final boolean deployEvenBuildFail;
  private final boolean noGpg;
  private final boolean force;
  private String server;
  private String proxy;
  private String username;
  private Secret password;
  private final String serverType;
  private String satelliteServerHostname;

  @DataBoundConstructor
  public RhnPush(String serverType,
                  String satelliteServerHostname,
                  String server,
                  String proxy,
                  String username,
                  Secret password,
                  boolean deployEvenBuildFail,
                  boolean noGpg,
                  boolean newest,
                  boolean force,
                  List<RhnPushEntry> publishedRpms) {
    this.entries = publishedRpms;
    this.deployEvenBuildFail = deployEvenBuildFail;
    this.noGpg = noGpg;
    this.newest = newest;
    this.force = force;
    if (this.entries == null) {
      this.entries = Collections.emptyList();
    }
    this.serverType = serverType;
    if (serverType.toLowerCase().equals("build")) {
      this.server = server;
      this.proxy = proxy;
      this.username = username;
      this.password = password;
    } else {
      this.satelliteServerHostname = satelliteServerHostname;
    }
  }

  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.NONE;
  }

  private boolean isPerformDeployment(AbstractBuild build) {
    Result result = build.getResult();
    return result == null || isDeployEvenBuildFail() || result.isBetterOrEqualTo(Result.UNSTABLE);

  }

  @SuppressWarnings("unused")
  public List<RhnPushEntry> getEntries() {
    return entries;
  }

  public boolean isDeployEvenBuildFail() {
    return deployEvenBuildFail;
  }

  public boolean isNoGpg() {
    return noGpg;
  }

  public boolean isNewest() {
    return newest;
  }

  public boolean isForce() {
    return force;
  }

  public String getServer() {
    return server;
  }

  public String getProxy() {
    return proxy;
  }

  public String getUsername() {
    return username;
  }

  public Secret getPassword() {
    return password;
  }


  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
    if (isPerformDeployment(build)) {
      listener.getLogger().println("[RhnPush] - Starting publishing RPMs ...");

      for (RhnPushEntry entry : entries) {
        StringTokenizer rpmGlobTokenizer = new StringTokenizer(entry.getIncludes(), ",");

        while (rpmGlobTokenizer.hasMoreTokens()) {
          String rpmGlob = rpmGlobTokenizer.nextToken();
          listener.getLogger().println("[RhnPush] - Publishing " + rpmGlob);

          FilePath workspace = build.getWorkspace();
          if (workspace == null) {
            throw new IllegalStateException("Could not get a workspace.");
          }
          FilePath[] matchedRpms = workspace.list(rpmGlob);
          if (ArrayUtils.isEmpty(matchedRpms)) {
            listener.getLogger().println("[RhnPush] - No RPMs matching " + rpmGlob);
          } else {
            ArgumentListBuilder command = new ArgumentListBuilder();

            command.add("rhnpush");

            if (getServerType().toLowerCase().equals("global")) {
              SatelliteServer ss = getSatelliteServer();
              if (ss != null) {
                command.add("--server=" + ss.getHostname(), "-u", ss.getUsername(), "-p");
                command.addMasked(ss.getPassword().getPlainText());
                if(! ss.getProxy().isEmpty()) {
                  command.add("--proxy="+ss.getProxy());
                }
              } else {
                listener.getLogger().println("[RhnPush] - Unknown global satellite server " + getSatelliteServerHostname() + " ...");
                return false;
              }
            } else {
              command.add("--server=" + server, "-u", getUsername(), "-p");
              command.addMasked(getPassword().getPlainText());
              if(! proxy.isEmpty()) {
                command.add("--proxy="+proxy);
              }
            }

            StringTokenizer channelTokenizer = new StringTokenizer(entry.getChannels(), ",");
            while (channelTokenizer.hasMoreTokens()) {
              command.add("-c");
              command.add(channelTokenizer.nextToken().trim());
            }

            if (isNoGpg()) {
              command.add("--nosig");
            }

            if (isNewest()) {
              command.add("--newest");
            }

            if (isForce()) {
              command.add("--force");
            }
            for (FilePath rpmFilePath : matchedRpms) {
              command.add(rpmFilePath.toURI().normalize().getPath());
            }

            Launcher.ProcStarter ps = launcher.new ProcStarter();
            ps = ps.cmds(command).stdout(listener);
            ps = ps.pwd(build.getWorkspace()).envs(build.getEnvironment(listener));

            Proc proc = launcher.launch(ps);
            int retcode = proc.join();
            if (retcode != 0) {
              listener.getLogger().println("[RhnPush] - Failed publishing RPMs ...");
              return false;
            }
          }
        }
      }

      listener.getLogger().println("[RhnPush] - Finished publishing RPMs ...");
    } else {
      listener.getLogger().println("[RhnPush] - Skipping publishing RPMs ...");
    }
    return true;
  }

  public String getServerType() {
    return serverType;
  }

  public String getSatelliteServerHostname() {
    return satelliteServerHostname;
  }

  private SatelliteServer getSatelliteServer() {
    Jenkins jenkins = Jenkins.getInstance();
    if (jenkins == null) {
      throw new IllegalStateException("Could not get a Jenkins instance.");
    }
    RpmPublisherDescriptor rpmPublisherDescriptor = jenkins.getDescriptorByType(RpmPublisherDescriptor.class);
    if (!StringUtils.isEmpty(getSatelliteServerHostname()) && !rpmPublisherDescriptor.getSatelliteServers().isEmpty()) {
      for (SatelliteServer ss : rpmPublisherDescriptor.getSatelliteServers()) {
        if (StringUtils.equals(getSatelliteServerHostname(), ss.getHostname())) {
          return ss;
        }
      }
    }
    return null;
  }

  @Extension
  @SuppressWarnings("unused")
  public static final class RpmPublisherDescriptor extends BuildStepDescriptor<Publisher> {

    public static final String DISPLAY_NAME = Messages.rhnpushpublisher_displayName();

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return true;
    }

    private volatile List<SatelliteServer> satelliteServers = new ArrayList<SatelliteServer>();

    public RpmPublisherDescriptor() {
      load();
    }

    @Override
    public String getDisplayName() {
      return Messages.rhnpushpublisher_displayName();
    }

    public List<SatelliteServer> getSatelliteServers() {
      return satelliteServers;
    }

    public ListBoxModel doFillSatelliteServerHostnameItems() {
      ListBoxModel items = new ListBoxModel();
      for (SatelliteServer satelliteServer : satelliteServers) {
        items.add(satelliteServer.getHostname(), satelliteServer.getHostname());
      }
      return items;
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
      satelliteServers = req.bindJSONToList(SatelliteServer.class, json.get("satelliteServer"));
      save();
      return true;
    }

    public FormValidation doCheckHostname(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
      return FormValidation.validateRequired(value);
    }

    public FormValidation doCheckServer(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
      return FormValidation.validateRequired(value);
    }

    public FormValidation doCheckUsername(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
      return FormValidation.validateRequired(value);
    }

    public FormValidation doCheckPassword(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
      return FormValidation.validateRequired(value);
    }

    public FormValidation doCheckChannels(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException {
      return FormValidation.validateRequired(value);
    }

    public FormValidation doCheckIncludes(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException, InterruptedException {
      FilePath workspace = project.getWorkspace();
      if (workspace == null) {
        throw new IllegalStateException("Could not get a workspace.");
      }
      String msg = workspace.validateAntFileMask(value);
      if (msg != null) {
        return FormValidation.error(msg);
      }
      return FormValidation.ok();
    }

  }
}
