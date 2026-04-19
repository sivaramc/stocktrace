package in.stocktrace.ticker;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.Tick;
import com.zerodhatech.ticker.KiteTicker;
import com.zerodhatech.ticker.OnConnect;
import com.zerodhatech.ticker.OnDisconnect;
import com.zerodhatech.ticker.OnError;
import com.zerodhatech.ticker.OnTicks;
import in.stocktrace.broker.zerodha.KiteConnectFactory;
import in.stocktrace.common.BrokerOperationException;
import in.stocktrace.user.KiteUser;
import in.stocktrace.user.KiteUserService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a per-user {@link KiteTicker} websocket connection. Subscriptions can
 * be added on the fly; ticks are optionally persisted to {@link TickLogRepository}.
 */
@Service
public class TickerService {

    private static final Logger log = LoggerFactory.getLogger(TickerService.class);

    private final KiteConnectFactory factory;
    private final KiteUserService userService;
    private final TickLogRepository tickRepo;
    private final boolean enabled;
    private final String mode;

    private final Map<String, ManagedTicker> tickers = new ConcurrentHashMap<>();

    public TickerService(KiteConnectFactory factory,
                         KiteUserService userService,
                         TickLogRepository tickRepo,
                         @Value("${stocktrace.ticker.enabled:true}") boolean enabled,
                         @Value("${stocktrace.ticker.mode:full}") String mode) {
        this.factory = factory;
        this.userService = userService;
        this.tickRepo = tickRepo;
        this.enabled = enabled;
        this.mode = mode;
    }

    public synchronized ManagedTicker connect(String brokerUserId) {
        if (!enabled) {
            throw new BrokerOperationException("Ticker is disabled (stocktrace.ticker.enabled=false)");
        }
        KiteUser user = userService.getRequired(brokerUserId);
        KiteConnect kite = factory.forUser(brokerUserId);

        ManagedTicker existing = tickers.get(brokerUserId);
        if (existing != null && existing.ticker.isConnectionOpen()) {
            return existing;
        }
        // Stop the old ticker (including its auto-reconnect loop) before replacing
        // it — otherwise a background reconnect could bring it back online and we'd
        // have two websockets for the same user both persisting ticks.
        if (existing != null) {
            try {
                existing.ticker.disconnect();
            } catch (Exception ignored) {
                // best-effort cleanup; the replacement happens regardless
            }
        }

        KiteTicker ticker = new KiteTicker(kite.getAccessToken(), kite.getApiKey());
        // Preserve previously-subscribed tokens across reconnects so the onConnected
        // handler can re-subscribe them instead of silently dropping the user's feed.
        Set<Long> tokens = existing != null ? existing.tokens : ConcurrentHashMap.newKeySet();
        ManagedTicker managed = new ManagedTicker(brokerUserId, ticker, tokens);
        ticker.setTryReconnection(true);
        try {
            ticker.setMaximumRetries(10);
            ticker.setMaximumRetryInterval(30);
        } catch (com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException ignored) {
            // fall back to library defaults
        }

        ticker.setOnConnectedListener(new OnConnect() {
            @Override
            public void onConnected() {
                log.info("ticker[{}] connected", brokerUserId);
                if (!managed.tokens.isEmpty()) {
                    ArrayList<Long> tokens = new ArrayList<>(managed.tokens);
                    ticker.subscribe(tokens);
                    ticker.setMode(tokens, resolveMode());
                }
            }
        });
        ticker.setOnDisconnectedListener(new OnDisconnect() {
            @Override
            public void onDisconnected() {
                log.info("ticker[{}] disconnected", brokerUserId);
            }
        });
        ticker.setOnErrorListener(new OnError() {
            @Override
            public void onError(Exception exception) {
                log.warn("ticker[{}] error: {}", brokerUserId, exception.getMessage());
            }

            @Override
            public void onError(com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException kiteException) {
                log.warn("ticker[{}] kite error: {}", brokerUserId, kiteException.message);
            }

            @Override
            public void onError(String error) {
                log.warn("ticker[{}] error: {}", brokerUserId, error);
            }
        });
        ticker.setOnTickerArrivalListener(new OnTicks() {
            @Override
            public void onTicks(ArrayList<Tick> ticks) {
                for (Tick t : ticks) {
                    persist(brokerUserId, t);
                }
            }
        });

        ticker.connect();
        tickers.put(brokerUserId, managed);
        log.info("ticker[{}] connect requested (user {})", brokerUserId, user.getUserId());
        return managed;
    }

    public synchronized void subscribe(String brokerUserId, Set<Long> tokens) {
        ManagedTicker managed = tickers.get(brokerUserId);
        if (managed == null) {
            managed = connect(brokerUserId);
        }
        managed.tokens.addAll(tokens);
        if (managed.ticker.isConnectionOpen()) {
            ArrayList<Long> list = new ArrayList<>(tokens);
            managed.ticker.subscribe(list);
            managed.ticker.setMode(list, resolveMode());
        }
    }

    public synchronized void unsubscribe(String brokerUserId, Set<Long> tokens) {
        ManagedTicker managed = tickers.get(brokerUserId);
        if (managed == null) return;
        managed.tokens.removeAll(tokens);
        if (managed.ticker.isConnectionOpen()) {
            managed.ticker.unsubscribe(new ArrayList<>(tokens));
        }
    }

    public synchronized void disconnect(String brokerUserId) {
        ManagedTicker managed = tickers.remove(brokerUserId);
        if (managed != null && managed.ticker.isConnectionOpen()) {
            managed.ticker.disconnect();
        }
    }

    public boolean isConnected(String brokerUserId) {
        ManagedTicker managed = tickers.get(brokerUserId);
        return managed != null && managed.ticker.isConnectionOpen();
    }

    public Set<Long> subscribedTokens(String brokerUserId) {
        ManagedTicker managed = tickers.get(brokerUserId);
        return managed == null ? Set.of() : Set.copyOf(managed.tokens);
    }

    @PreDestroy
    public synchronized void shutdown() {
        tickers.values().forEach(m -> {
            try {
                if (m.ticker.isConnectionOpen()) {
                    m.ticker.disconnect();
                }
            } catch (Exception ignored) {
                // best effort
            }
        });
        tickers.clear();
    }

    private void persist(String brokerUserId, Tick t) {
        try {
            TickLog row = new TickLog();
            row.setKiteUserId(brokerUserId);
            row.setInstrumentToken(t.getInstrumentToken());
            row.setLastPrice(t.getLastTradedPrice());
            row.setVolumeTradedToday(t.getVolumeTradedToday());
            if (t.getLastTradedTime() != null) {
                row.setExchangeTimestamp(t.getLastTradedTime().toInstant());
            }
            if (t.getTickTimestamp() != null) {
                row.setTickTimestamp(t.getTickTimestamp().toInstant());
            }
            row.setReceivedAt(Instant.now());
            tickRepo.save(row);
        } catch (Exception ex) {
            log.debug("failed to persist tick for user {}: {}", brokerUserId, ex.getMessage());
        }
    }

    private String resolveMode() {
        return switch (mode == null ? "full" : mode.toLowerCase()) {
            case "ltp" -> KiteTicker.modeLTP;
            case "quote" -> KiteTicker.modeQuote;
            default -> KiteTicker.modeFull;
        };
    }

    public record ManagedTicker(String brokerUserId, KiteTicker ticker, Set<Long> tokens) {}
}
