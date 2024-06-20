package io.github.usernugget.limboauth.expansion.endpoint;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import io.github.usernugget.limboauth.expansion.LimboAuthExpansion;

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

  public abstract void write(ByteArrayDataOutput output);

  public abstract void read(ByteArrayDataInput input);
}
