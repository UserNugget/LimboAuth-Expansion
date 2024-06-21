package io.github.usernugget.limboauth.expansion.endpoint.type;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import io.github.usernugget.limboauth.expansion.LimboAuthExpansion;
import io.github.usernugget.limboauth.expansion.endpoint.Endpoint;
import java.util.Locale;

public class StringEndpoint extends Endpoint {

  private String value;

  public StringEndpoint(LimboAuthExpansion expansion, String type) {
    super(expansion, type);
  }

  public StringEndpoint(LimboAuthExpansion expansion, String type, String username) {
    super(expansion, type);
    this.username = username.toLowerCase(Locale.ROOT);
  }

  public String getValue() {
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
      throw this.expansion.showError(this.type + " endpoint is disabled at Velocity side");
    } else if (type == -2) {
      throw this.expansion.showError(this.type + " endpoint is unknown at Velocity side");
    } else if (type != 0) {
      throw this.expansion.showError(this.type + " endpoint has unsupported version, ensure that extension and plugin is up-to-date.");
    }

    this.username = input.readUTF().toLowerCase(Locale.ROOT);
    this.value = input.readUTF();
  }
}
