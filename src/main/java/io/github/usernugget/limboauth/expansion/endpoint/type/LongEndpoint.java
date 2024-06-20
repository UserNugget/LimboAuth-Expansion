package io.github.usernugget.limboauth.expansion.endpoint.type;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import io.github.usernugget.limboauth.expansion.LimboAuthExpansion;
import io.github.usernugget.limboauth.expansion.endpoint.Endpoint;
import java.util.Locale;

public class LongEndpoint extends Endpoint {

  private Long value;

  public LongEndpoint(LimboAuthExpansion expansion, String type) {
    super(expansion, type);
  }

  public LongEndpoint(LimboAuthExpansion expansion, String type, String username) {
    super(expansion, type);
    this.username = username.toLowerCase(Locale.ROOT);
  }

  public Long getValue() {
    return this.value;
  }

  @Override
  public void write(ByteArrayDataOutput output) {
    output.writeUTF(this.type);
    output.writeInt(0);
    output.writeUTF(this.username);
  }

  @Override
  public void read(ByteArrayDataInput input) {
    int type = input.readInt();
    if (type == -1) {
      throw new IllegalStateException(this.type + " endpoint is disabled at Velocity side");
    } else if (type == -2) {
      throw new IllegalStateException(this.type + " endpoint is unknown at Velocity side");
    } else if (type != 0) {
      throw new IllegalStateException(this.type + " endpoint has unsupported version, ensure that extension and plugin is up-to-date.");
    }

    this.username = input.readUTF().toLowerCase(Locale.ROOT);
    this.value = input.readLong();
  }
}
