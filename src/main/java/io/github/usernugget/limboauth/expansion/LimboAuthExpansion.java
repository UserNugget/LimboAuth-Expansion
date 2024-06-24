package io.github.usernugget.limboauth.expansion;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.usernugget.limboauth.expansion.endpoint.Endpoint;
import io.github.usernugget.limboauth.expansion.endpoint.type.LongEndpoint;
import io.github.usernugget.limboauth.expansion.endpoint.type.StringEndpoint;
import io.github.usernugget.limboauth.expansion.listener.PrefetchListener;
import io.github.usernugget.limboauth.expansion.stream.Input;
import io.github.usernugget.limboauth.expansion.throwable.QuietIllegalStateException;
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
  private Player communicationPlayer;

  private SimpleDateFormat dateFormat;
  private String premium;
  private String cracked;
  private String unknown;
  private String error;
  private String requesting;
  private String unfetched;
  private long purgeCacheMillis;
  private long requestTimeout;
  private boolean enablePrefetch;
  private boolean logErrors;
  private boolean quietErrors;
  private String token;

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

  public boolean isLogErrors() {
    return this.logErrors;
  }

  public String getToken() {
    return this.token;
  }

  @Override
  public Map<String, Object> getDefaults() {
    Map<String, Object> config = new LinkedHashMap<>();
    config.put("premium", "&6Premium account");
    config.put("cracked", "&cCracked account");
    config.put("unknown", "&aUnknown account");
    config.put("error", "&4Database error");
    config.put("requesting", "&4Requesting...");
    config.put("unfetched", "&7Unfetched");
    config.put("purge_cache_millis", 30_000);
    config.put("request_timeout", 5_000);
    config.put("enable_prefetch", true);
    config.put("log_errors", true);
    config.put("quiet_errors", true);
    config.put("token", "paste_limboauth_token_here");
    return config;
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
    this.unfetched = this.getMessage("unfetched");
    this.purgeCacheMillis = this.getLong("purge_cache_millis", 20_000);
    this.requestTimeout = this.getLong("request_timeout", 5_000);
    this.enablePrefetch = this.getBoolean("enable_prefetch", true);
    this.logErrors = this.getBoolean("log_errors", true);
    this.quietErrors = this.getBoolean("quiet_errors", true);
    this.token = this.getString("token", "paste_limboauth_token_here");

    return super.register();
  }

  private boolean getBoolean(String path, boolean defaultValue) {
    Object object = this.get(path, defaultValue);
    if (object == null) {
      object = defaultValue;
    }

    return (boolean) object;
  }

  private String getMessage(String path) {
    String message = this.getString(path, "...");
    if (message == null) {
      message = "...";
    }

    return ChatColor.translateAlternateColorCodes('&', message);
  }

  public IllegalStateException showError(String message) {
    if (this.quietErrors) {
      return new QuietIllegalStateException(message);
    } else {
      return new IllegalStateException(message);
    }
  }

  @Override
  public void start() {
    this.communicationPlayer = null;
    Bukkit.getMessenger().registerOutgoingPluginChannel(this.getPlaceholderAPI(), MESSAGE_CHANNEL);
    Bukkit.getMessenger().registerIncomingPluginChannel(this.getPlaceholderAPI(), MESSAGE_CHANNEL, this);

    if (this.enablePrefetch) {
      Bukkit.getPluginManager().registerEvents(new PrefetchListener(this), this.getPlaceholderAPI());
    }
  }

  @Override
  public void stop() {
    Bukkit.getMessenger().unregisterOutgoingPluginChannel(this.getPlaceholderAPI(), MESSAGE_CHANNEL);
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

  @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
  public <T extends Endpoint> T find(String type, String username) {
    Map<String, CompletableFuture<Endpoint>> values = this.requests.get(type);
    if (values == null) {
      return null;
    }

    CompletableFuture<T> future = (CompletableFuture<T>) values.get(username);
    if (future == null || future.isCancelled() || future.isCompletedExceptionally()) {
      return null;
    }

    return future.getNow(null);
  }

  public <T extends Endpoint> CompletableFuture<T> requestFuture(Player player, T endpoint) {
    CompletableFuture<Endpoint> request = this.requests.computeIfAbsent(endpoint.getType(), key -> new ConcurrentHashMap<>())
        .computeIfAbsent(endpoint.getUsername(), (username) -> {
          this.send(player, endpoint);
          return new CompletableFuture<Endpoint>()
              .orTimeout(this.requestTimeout, TimeUnit.MILLISECONDS);
        });

    if (request.isCancelled() || request.isCompletedExceptionally()) {
      if (this.logErrors) {
        this.getPlaceholderAPI().getLogger().log(Level.SEVERE,
            "Failed to request '" + endpoint.getType() + "' for '" + endpoint.getUsername() + "' from LimboAuth, retrying...");
      }

      this.requests.get(endpoint.getType()).remove(endpoint.getUsername());
      return this.requestFuture(player, endpoint);
    }

    Endpoint response = request.getNow(endpoint);
    if (response.getCreatedAt() != 0 && System.currentTimeMillis() - response.getCreatedAt() >= this.purgeCacheMillis) {
      this.send(player, endpoint);
    }

    return (CompletableFuture<T>) request;
  }

  public <T extends Endpoint> T request(Player player, T endpoint) {
    CompletableFuture<Endpoint> request = this.requestFuture(player, endpoint);
    if (request == null) {
      return endpoint;
    }

    return (T) request.getNow(endpoint);
  }

  public <T extends Endpoint> void send(Player player, T endpoint) {
    ByteArrayDataOutput output = ByteStreams.newDataOutput();
    endpoint.write(output);
    if (!player.isOnline()) {
      throw this.showError("trying to communiate via disconnected connection");
    } else if (!player.getListeningPluginChannels().contains(MESSAGE_CHANNEL)) {
      throw this.showError("trying to communicate via connection that was connected 'outside of LimboAuth'");
    }

    player.sendPluginMessage(this.getPlaceholderAPI(), MESSAGE_CHANNEL, output.toByteArray());
  }

  @Override
  public String onRequest(OfflinePlayer player, String params) {
    try {
      Player onlinePlayer = null;
      if (player != null) {
        onlinePlayer = Bukkit.getPlayer(player.getUniqueId());
        if (onlinePlayer != null && !onlinePlayer.getListeningPluginChannels().contains(MESSAGE_CHANNEL)) {
          onlinePlayer = null;
        }
      }

      if (onlinePlayer == null) {
        onlinePlayer = this.findCommunicator();
      }

      for (String key : TYPES.keySet()) {
        if (params.startsWith(key)) {
          String username = null;
          if (params.startsWith(key + "_")) {
            username = params.substring(key.length() + 1);
          } else if (player != null) {
            username = player.getName();
          }

          if (username == null) {
            break;
          }

          if (key.equals("premium_state")) {
            StringEndpoint response = this.find(key, username);
            if (response == null) {
              response = this.request(onlinePlayer, new StringEndpoint(this, key, username));
            }

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
            StringEndpoint response = this.find(key, username);
            if (response == null) {
              response = this.request(onlinePlayer, new StringEndpoint(this, key, username));
            }

            if (response.getValue() != null) {
              return response.getValue();
            } else {
              return this.requesting;
            }
          } else if (DATE_TYPES.contains(key)) {
            LongEndpoint response = this.find(key, username);
            if (response == null) {
              response = this.request(onlinePlayer, new LongEndpoint(this, key, username));
            }

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
    } catch (Throwable throwable) {
      if (this.logErrors) {
        this.getPlaceholderAPI().getLogger().log(Level.SEVERE,
            "Failed to handle placeholder '" + params + "'", throwable);
      }
      return this.unfetched;
    }
  }

  private Player findCommunicator() {
    if (this.communicationPlayer == null || !this.communicationPlayer.isOnline()) {
      for (Player player : Bukkit.getOnlinePlayers()) {
        if (player.getListeningPluginChannels().contains(MESSAGE_CHANNEL)) {
          this.communicationPlayer = player;
          break;
        }
      }

      if (this.communicationPlayer == null || !this.communicationPlayer.isOnline()) {
        throw this.showError("failed to find any connections that use LimboAuth");
      }
    }

    return this.communicationPlayer;
  }

  @Override
  public void onPluginMessageReceived(String channel, Player player, byte[] message) {
    if (!MESSAGE_CHANNEL.equals(channel)) {
      return;
    }

    try {
      Input in = new Input(message);
      String dataType = in.readUtf(24);
      Function<LimboAuthExpansion, Endpoint> typeFunc = TYPES.get(dataType);
      if (typeFunc == null) {
        throw this.showError("received unknown endpoint type: " + dataType);
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
    } catch (Throwable throwable) {
      if (this.logErrors) {
        this.getPlaceholderAPI().getLogger().log(Level.SEVERE,
            "Failed to handle LimboAuth response:", throwable);
      }
    }
  }

  public void remove(String name) {
    for (Entry<String, Map<String, CompletableFuture<Endpoint>>> entry : this.requests.entrySet()) {
      entry.getValue().remove(name);
    }
  }
}
