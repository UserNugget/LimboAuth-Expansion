package io.github.usernugget.limboauth.expansion.endpoint;

import com.google.common.io.ByteArrayDataOutput;
import io.github.usernugget.limboauth.expansion.LimboAuthExpansion;
import io.github.usernugget.limboauth.expansion.stream.Input;
import io.github.usernugget.limboauth.expansion.throwable.QuietIllegalStateException;

public abstract class Endpoint {

  protected final LimboAuthExpansion expansion;
  protected final String type;
  protected long createdAt;

  protected String username;

  public Endpoint(LimboAuthExpansion expansion, String type) {
    this.expansion = expansion;
    this.type = type;
  }

  public long getCreatedAt() {
    return this.createdAt;
  }

  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }

  public String getType() {
    return this.type;
  }

  public String getUsername() {
    return this.username;
  }

  public void write(ByteArrayDataOutput output) {
    output.writeUTF(this.type);
    output.writeInt(0);
    output.writeUTF(this.username);
  }

  public void read(Input input) {
    int type = input.readInt();
    if (type == -1) {
      throw this.expansion.showError(this.type + " endpoint is disabled at Velocity side");
    } else if (type == -2) {
      throw this.expansion.showError(this.type + " endpoint is unknown at Velocity side");
    } else if (type != 1) {
      throw this.expansion.showError(this.type + " endpoint has unsupported version, ensure that extension and plugin is up-to-date.");
    }

    try {
      if (!this.expansion.getToken().equals(input.readUtf(this.expansion.getToken().length()))) {
        throw new QuietIllegalStateException();
      }
    } catch (QuietIllegalStateException throwable) {
      throw this.expansion.showError(this.type + " endpoint was impersonated.");
    }

    this.username = input.readUtf(192);
    this.readContents(input);
  }

  public void writeContents(ByteArrayDataOutput output) {

  }

  public abstract void readContents(Input input);
}
