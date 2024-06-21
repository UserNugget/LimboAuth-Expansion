package io.github.usernugget.limboauth.expansion.listener;

import io.github.usernugget.limboauth.expansion.LimboAuthExpansion;
import io.github.usernugget.limboauth.expansion.endpoint.type.LongEndpoint;
import io.github.usernugget.limboauth.expansion.endpoint.type.StringEndpoint;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;

public class PrefetchListener implements Listener {

  private final LimboAuthExpansion expansion;

  public PrefetchListener(LimboAuthExpansion expansion) {
    this.expansion = expansion;
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onLoad(PlayerRegisterChannelEvent event) {
    if (event.getChannel().equals(LimboAuthExpansion.MESSAGE_CHANNEL)) {
      this.expansion.requestFuture(event.getPlayer(), new StringEndpoint(this.expansion, "available_endpoints", event.getPlayer().getName()))
          .thenAccept(available -> {
            if (available.getValue() == null) {
              return;
            }

            for (String endpoint : available.getValue().split(",")) {
              if (LimboAuthExpansion.TYPES.containsKey(endpoint)) {
                if (LimboAuthExpansion.DATE_TYPES.contains(endpoint)) {
                  this.expansion.request(event.getPlayer(), new LongEndpoint(this.expansion, endpoint, event.getPlayer().getName()));
                } else {
                  this.expansion.request(event.getPlayer(), new StringEndpoint(this.expansion, endpoint, event.getPlayer().getName()));
                }
              }
            }
          });
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    this.expansion.remove(event.getPlayer().getName());
  }
}
