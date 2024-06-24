package io.github.usernugget.limboauth.expansion.endpoint.type;

import io.github.usernugget.limboauth.expansion.LimboAuthExpansion;
import io.github.usernugget.limboauth.expansion.endpoint.Endpoint;
import io.github.usernugget.limboauth.expansion.stream.Input;
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
  public void readContents(Input input) {
    this.value = input.readLong();
  }
}
