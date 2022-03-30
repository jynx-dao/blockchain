package com.jynx.pro.handler;

import com.jynx.pro.constant.WebSocketChannelType;
import com.jynx.pro.entity.Market;
import com.jynx.pro.entity.User;
import com.jynx.pro.model.WebSocketSubscription;
import com.jynx.pro.repository.ReadOnlyRepository;
import com.jynx.pro.utils.JSONUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SocketHandler extends TextWebSocketHandler {

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Autowired
    private JSONUtils jsonUtils;
    @Autowired
    private ReadOnlyRepository readOnlyRepository;

    @Getter
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    @Getter
    private final Map<String, WebSocketSubscription> subscriptions = new HashMap<>();

    /**
     * Send a message to the matching sessions
     *
     * @param type {@link WebSocketChannelType}
     * @param marketId the market ID
     * @param message the message to send
     */
    public void sendMessage(
            final WebSocketChannelType type,
            final UUID marketId,
            final Object message
    ) {
        sendMessage(type, null, marketId, message);
    }

    /**
     * Send a message to the matching sessions
     *
     * @param type {@link WebSocketChannelType}
     * @param publicKey the public key
     * @param message the message to send
     */
    public void sendMessage(
            final WebSocketChannelType type,
            final String publicKey,
            final Object message
    ) {
        sendMessage(type, publicKey, null, message);
    }

    /**
     * Send a message to the matching sessions
     *
     * @param type {@link WebSocketChannelType}
     * @param message the message to send
     */
    public void sendMessage(
            final WebSocketChannelType type,
            final Object message
    ) {
        sendMessage(type, null, null, message);
    }

    /**
     * Send a message to the matching sessions
     *
     * @param type {@link WebSocketChannelType}
     * @param publicKey the public key
     * @param marketId the market ID
     * @param message the message to send
     */
    public void sendMessage(
            final WebSocketChannelType type,
            final String publicKey,
            final UUID marketId,
            final Object message
    ) {
        executorService.submit(() -> {
            List<WebSocketSession> sessions = getSessions(type, publicKey, marketId.toString());
            sessions.forEach(s -> {
                try {
                    s.sendMessage(new TextMessage(jsonUtils.toJson(message)));
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            });
        });
    }

    /**
     * Get the web socket sessions that have subscribed to the relevant update
     *
     * @param type {@link WebSocketChannelType}
     * @param publicKey the user's public key
     * @param marketId the market ID
     *
     * @return {@link List<WebSocketSession>}
     */
    public List<WebSocketSession> getSessions(
            final WebSocketChannelType type,
            final String publicKey,
            final String marketId
    ) {
       List<String> ids = subscriptions.entrySet().stream().filter(es -> {
           List<WebSocketChannelType> channelTypes = es.getValue().getChannels()
                   .stream().map(WebSocketSubscription.WebSocketChannel::getType)
                   .collect(Collectors.toList());
           boolean result = channelTypes.contains(type);
           if(Objects.nonNull(publicKey)) {
               result = result && es.getValue().getChannels().stream()
                       .anyMatch(c -> (c.getPublicKey() == null ||
                               c.getPublicKey().equals(publicKey)) && c.getType().equals(type));
           }
           if(Objects.nonNull(marketId)) {
               result = result && es.getValue().getChannels().stream()
                       .anyMatch(c -> (c.getMarketId() == null ||
                               c.getMarketId().toString().equals(marketId)) && c.getType().equals(type));
           }
           return result;
       }).map(Map.Entry::getKey).collect(Collectors.toList());
       return sessions.stream().filter(s -> ids.contains(s.getId())).collect(Collectors.toList());
    }

    /**
     * Close web socket session
     *
     * @param session {@link WebSocketSession}
     */
    private void closeSession(
            final WebSocketSession session
    ) {
        try {
            session.close();
        } catch(Exception e) {
            log.warn(e.getMessage(), e);
        }
    }

    /**
     * Validate a new web socket subscription
     *
     * @param sub {@link WebSocketSubscription}
     * @param session {@link WebSocketSession}
     */
    private void validateSubscription(
            final WebSocketSubscription sub,
            final WebSocketSession session
    ) {
        sub.getChannels().forEach(c -> {
            if(Objects.nonNull(c.getPublicKey())) {
                Optional<User> userOptional = readOnlyRepository.getUserByPublicKey(c.getPublicKey());
                if(userOptional.isEmpty()) {
                    closeSession(session);
                }
            }
            if(Objects.nonNull(c.getMarketId())) {
                Optional<Market> marketOptional = readOnlyRepository.getMarketById(c.getMarketId());
                if(marketOptional.isEmpty()) {
                    closeSession(session);
                }
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleTextMessage(@NotNull WebSocketSession session, @NotNull TextMessage message) {
        WebSocketSubscription sub = jsonUtils.fromJson(message.getPayload(), WebSocketSubscription.class);
        validateSubscription(sub, session);
        subscriptions.put(session.getId(), sub);
//        session.sendMessage(new TextMessage("Hello " + message.getPayload() + " !"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterConnectionEstablished(@NotNull WebSocketSession session) {
        sessions.add(session);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) {
        subscriptions.remove(session.getId());
        sessions.removeIf(s -> s.getId().equals(session.getId()));
    }
}
