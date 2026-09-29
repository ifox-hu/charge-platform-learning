package com.chargeplatform.realtime;

import com.chargeplatform.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RealtimeStatusWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper mapper;
    private final JwtService jwtService;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    public RealtimeStatusWebSocketHandler(ObjectMapper mapper, JwtService jwtService) {
        this.mapper = mapper;
        this.jwtService = jwtService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String token = queryToken(session.getUri());
        if (token == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            jwtService.parse(token);
        } catch (IllegalArgumentException exception) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessions.add(session);
    }

    public void publish(Object payload) {
        String message;
        try {
            message = mapper.writeValueAsString(Map.of("type", "SIMULATOR_STATUS", "data", payload));
        } catch (Exception exception) {
            return;
        }
        sessions.removeIf(session -> !session.isOpen());
        sessions.forEach(session -> {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception exception) {
                sessions.remove(session);
                try { session.close(CloseStatus.SERVER_ERROR); } catch (Exception ignored) { }
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private String queryToken(URI uri) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String part : uri.getQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && "token".equals(pair[0]) && !pair[1].isBlank()) return pair[1];
        }
        return null;
    }
}
