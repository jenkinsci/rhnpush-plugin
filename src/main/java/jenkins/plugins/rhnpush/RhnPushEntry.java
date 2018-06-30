package jenkins.plugins.rhnpush;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class RhnPushEntry implements Serializable {

  static final long serialVersionUID = 42L;

  @Deprecated
  @SuppressWarnings("unused")
  private transient String id;

  private String includes;
  private String channels;

  public RhnPushEntry() {
  }

  @DataBoundConstructor
  public RhnPushEntry(String includes, String channels) {
    this.includes = includes;
    this.channels = channels;
  }

  @SuppressWarnings("unused")
  public String getIncludes() {
    return includes;
  }

  @SuppressWarnings("unused")
  public String getChannels() {
    return channels;
  }

  @SuppressWarnings({"unused", "deprecation"})
  @Deprecated
  public String getId() {
    return id;
  }

  public int getUniqueId() {
    int result = includes != null ? includes.hashCode() : 0;
    result = 31 * result + (channels != null ? channels.hashCode() : 0);
    return result;
  }
}
