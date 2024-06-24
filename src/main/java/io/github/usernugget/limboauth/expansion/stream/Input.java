package io.github.usernugget.limboauth.expansion.stream;

import io.github.usernugget.limboauth.expansion.throwable.QuietIllegalStateException;
import java.nio.charset.StandardCharsets;

public class Input {

  private static final QuietIllegalStateException LONG_UNDERFLOW =
      new QuietIllegalStateException("long underflow");
  private static final QuietIllegalStateException INT_UNDERFLOW =
      new QuietIllegalStateException("int underflow");
  private static final QuietIllegalStateException SHORT_UNDERFLOW =
      new QuietIllegalStateException("short underflow");
  private static final QuietIllegalStateException ARRAY_UNDERFLOW =
      new QuietIllegalStateException("array underflow");
  private static final QuietIllegalStateException UTF_OVERFLOW =
      new QuietIllegalStateException("utf overflow");
  private static final QuietIllegalStateException UTF_SIZE_MISMATCH =
      new QuietIllegalStateException("utf size mismatch");

  private byte[] data;
  private int offset;

  public Input(byte[] data) {
    this.data = data;
  }

  public long readLong() {
    this.ensureReadable(8, INT_UNDERFLOW);
    return ((long) this.readUnsigned() << 56)
        + ((long) this.readUnsigned() << 48)
        + ((long) this.readUnsigned() << 40)
        + ((long) this.readUnsigned() << 32)
        + ((long) this.readUnsigned() << 24)
        + ((long) this.readUnsigned() << 16)
        + ((long) this.readUnsigned() << 8)
        + this.readUnsigned();
  }
  
  public int readInt() {
    this.ensureReadable(4, INT_UNDERFLOW);
    return ((this.readUnsigned() << 24) + (this.readUnsigned() << 16) + (this.readUnsigned() << 8) + this.readUnsigned());
  }

  public int readShort() {
    this.ensureReadable(2, SHORT_UNDERFLOW);
    return (short) ((this.readUnsigned() << 8) + this.readUnsigned());
  }

  public byte[] readFully(int size) {
    this.ensureReadable(size, ARRAY_UNDERFLOW);
    byte[] data = new byte[size];
    System.arraycopy(this.data, this.offset, data, 0, size);
    this.offset += size;
    return data;
  }
  
  public String readUtf(int maxSize) {
    int size = this.readShort() & 0xFFFF;
    if (size > maxSize) {
      throw UTF_OVERFLOW;
    }

    return new String(this.readFully(size), StandardCharsets.UTF_8);
  }

  public int readUnsigned() {
    return this.data[this.offset++] & 0xFF;
  }

  private void ensureReadable(int size, QuietIllegalStateException underflow) {
    if (this.offset + size > this.data.length) {
      throw underflow;
    }
  }
}
