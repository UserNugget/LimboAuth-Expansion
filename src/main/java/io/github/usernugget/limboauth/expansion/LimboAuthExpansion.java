package io.github.usernugget.limboauth.expansion;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.github.usernugget.limboauth.expansion.endpoint.Endpoint;
import io.github.usernugget.limboauth.expansion.endpoint.type.LongEndpoint;
import io.github.usernugget.limboauth.expansion.endpoint.type.StringEndpoint;
import io.github.usernugget.limboauth.expansion.listener.PrefetchListener;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Taskable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class LimboAuthExpansion extends PlaceholderExpansion implements PluginMessageListener, Taskable, Configurable {

  public static final String MESSAGE_CHANNEL = "limboauth:backend_api";

  public static final Map<String, Function<LimboAuthExpansion, Endpoint>> TYPES;
  public static final Set<String> STRING_TYPES;
  public static final Set<String> DATE_TYPES;

  static {
    Map<String, Function<LimboAuthExpansion, Endpoint>> types = new HashMap<>();
    types.put("available_endpoints", plugin -> new StringEndpoint(plugin, "available_endpoints"));
    types.put("premium_state", plugin -> new StringEndpoint(plugin, "premium_state"));
    types.put("hash", plugin -> new StringEndpoint(plugin, "hash"));
    types.put("totp_token", plugin -> new StringEndpoint(plugin, "totp_token"));
    types.put("reg_date", plugin -> new LongEndpoint(plugin, "reg_date"));
    types.put("uuid", plugin -> new StringEndpoint(plugin, "uuid"));
    types.put("premium_uuid", plugin -> new StringEndpoint(plugin, "premium_uuid"));
    types.put("ip", plugin -> new StringEndpoint(plugin, "ip"));
    types.put("login_ip", plugin -> new StringEndpoint(plugin, "login_ip"));
    types.put("login_date", plugin -> new LongEndpoint(plugin, "login_date"));
    types.put("token_issued_at", plugin -> new LongEndpoint(plugin, "token_issued_at"));
    TYPES = Collections.unmodifiableMap(types);
    STRING_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "hash", "totp_token", "uuid", "premium_uuid", "login_ip", "ip"
    )));
    DATE_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "reg_date", "login_date", "token_issued_at"
    )));
  }

  private Map<String, Map<String, CompletableFuture<Endpoint>>> requests = new ConcurrentHashMap<>();
  private SimpleDateFormat dateFormat;
  private String premium;
  private String cracked;
  private String unknown;
  private String error;
  private String requesting;
  private long purgeCacheMillis;
  private long requestTimeout;

  @Override
  public String getIdentifier() {
    return "limboauth";
  }

  @Override
  public String getName() {
    return "LimboAuth";
  }

  @Override
  public String getAuthor() {
    return "UserNugget";
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public Map<String, Object> getDefaults() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("premium", "&6Premium account");
    config.put("cracked", "&cCracked account");
    config.put("unknown", "&aUnknown account");
    config.put("error", "&4Database error");
    config.put("requesting", "&4Requesting...");
    config.put("purge_cache_millis", 30_000);
    config.put("request_timeout", 5_000);
    return config;
  }

  private String getMessage(String path) {
    String message = this.getString(path, "...");

    // spotbugs {:
    if (message == null) {
      message = "...";
    }

    return ChatColor.translateAlternateColorCodes('&', message);
  }

  @Override
  public boolean register() {
    this.requests = new ConcurrentHashMap<>();
    this.dateFormat = PlaceholderAPIPlugin.getDateFormat();

    this.premium = this.getMessage("premium");
    this.cracked = this.getMessage("cracked");
    this.unknown = this.getMessage("unknown");
    this.error = this.getMessage("error");
    this.requesting = this.getMessage("requesting");
    this.purgeCacheMillis = this.getLong("purge_cache_millis", 5_000);
    this.requestTimeout = this.getLong("request_timeout", 5_000);

    return super.register();
  }

  @Override
  public void start() {
    Bukkit.getMessenger().registerOutgoingPluginChannel(this.getPlaceholderAPI(), MESSAGE_CHANNEL);
    Bukkit.getMessenger().registerIncomingPluginChannel(this.getPlaceholderAPI(), MESSAGE_CHANNEL, this);

    Bukkit.getPluginManager().registerEvents(new PrefetchListener(this), this.getPlaceholderAPI());
  }

  @Override
  public void stop() {
    Bukkit.getMessenger().unregisterIncomingPluginChannel(this.getPlaceholderAPI(), MESSAGE_CHANNEL);
    Bukkit.getMessenger().unregisterIncomingPluginChannel(this.getPlaceholderAPI(), MESSAGE_CHANNEL, this);

    this.unregisterEvent(PlayerRegisterChannelEvent.getHandlerList());
    this.unregisterEvent(PlayerQuitEvent.getHandlerList());
  }

  private void unregisterEvent(HandlerList handlerList) {
    for (RegisteredListener listener : handlerList.getRegisteredListeners()) {
      if (listener.getListener() instanceof PrefetchListener) {
        handlerList.unregister(listener.getListener());
      }
    }
  }

  public <T extends Endpoint> CompletableFuture<T> requestFuture(T endpoint) {
    if (Bukkit.getOnlinePlayers().isEmpty()) {
      return CompletableFuture.completedFuture(endpoint);
    }

    Player player = Bukkit.getOnlinePlayers().iterator().next();
    CompletableFuture<Endpoint> request = this.requests.computeIfAbsent(endpoint.getType(), key -> new ConcurrentHashMap<>())
        .computeIfAbsent(endpoint.getUsername(), (username) -> {
          this.send(player, endpoint);
          return new CompletableFuture<Endpoint>()
              .orTimeout(this.requestTimeout, TimeUnit.MILLISECONDS);
        });

    if (request.isCancelled() || request.isCompletedExceptionally()) {
      this.getPlaceholderAPI().getLogger().log(Level.SEVERE,
          "Failed to request data from LimboAuth, retrying...");

      this.requests.get(endpoint.getType()).remove(endpoint.getUsername());
      return this.requestFuture(endpoint);
    }

    Endpoint response = request.getNow(endpoint);
    if (response.getCreatedAt() != 0 && System.currentTimeMillis() - response.getCreatedAt() >= this.purgeCacheMillis) {
      this.send(player, endpoint);
    }

    return (CompletableFuture<T>) request;
  }

  public <T extends Endpoint> T request(T endpoint) {
    CompletableFuture<Endpoint> request = this.requestFuture(endpoint);
    if (request == null) {
      return endpoint;
    }

    return (T) request.getNow(endpoint);
  }

  public <T extends Endpoint> void send(Player player, T endpoint) {
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    endpoint.write(output);
    if (!player.getListeningPluginChannels().contains(MESSAGE_CHANNEL)) {
      throw new IllegalStateException("player messaging channel was not found");
    }

    player.sendPluginMessage(this.getPlaceholderAPI(), MESSAGE_CHANNEL, output.toByteArray());
  }

  @Override
  public String onRequest(OfflinePlayer player, String params) {
    for (String key : TYPES.keySet()) {
      if (params.startsWith(key)) {
        String username = null;
        if (params.startsWith(key + "_")) {
          username = params.substring(key.length() + 1);
        } else if (player != null) {
          username = player.getName();
        }

        if (username == null) {
          continue;
        }

        if (key.equals("premium_state")) {
          StringEndpoint response = this.request(new StringEndpoint(this, key, username));
          if (response.getValue() != null) {
            switch (response.getValue()) {
              case "PREMIUM": {
                return this.premium;
              }
              case "CRACKED": {
                return this.cracked;
              }
              case "UNKNOWN": {
                return this.unknown;
              }
              default: {
                return this.error;
              }
            }
          } else {
            return this.requesting;
          }
        } else if (STRING_TYPES.contains(key)) {
          StringEndpoint response = this.request(new StringEndpoint(this, key, username));
          if (response.getValue() != null) {
            return response.getValue();
          } else {
            return this.requesting;
          }
        } else if (DATE_TYPES.contains(key)) {
          LongEndpoint response = this.request(new LongEndpoint(this, key, username));
          if (response.getValue() != null) {
            if (response.getValue() == Long.MIN_VALUE) {
              return this.unknown;
            }

            return this.dateFormat.format(new Date(response.getValue()));
          } else {
            return this.requesting;
          }
        }
      }
    }

    return null;
  }

  @Override
  public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!MESSAGE_CHANNEL.equals(channel)) {
      return;
    }

    ByteArrayDataInput in = ByteStreams.newDataInput(message);
    try {
      String dataType = in.readUTF();
      Function<LimboAuthExpansion, Endpoint> typeFunc = TYPES.get(dataType);
      if (typeFunc == null) {
        throw new IllegalStateException("received unknown endpoint type: " + dataType);
      }

      Endpoint endpoint = typeFunc.apply(this);
      endpoint.read(in);

      Map<String, CompletableFuture<Endpoint>> requests = this.requests.get(endpoint.getType());
      if (requests == null) {
        return;
      }

      CompletableFuture<Endpoint> request = requests.get(endpoint.getUsername());
      if (request != null) {
        endpoint.setCreatedAt(System.currentTimeMillis());
        if (!request.isDone()) {
          request.complete(endpoint);
        } else {
          requests.put(endpoint.getUsername(), CompletableFuture.completedFuture(endpoint));
        }
      }
    } catch (Exception e) {
      this.getPlaceholderAPI().getLogger().log(Level.SEVERE,
          "Failed to handle LimboAuth response:", e);
    }
  }

  public void remove(String name) {
    for (Entry<String, Map<String, CompletableFuture<Endpoint>>> entry : this.requests.entrySet()) {
      entry.getValue().remove(name);
    }
  }
}
