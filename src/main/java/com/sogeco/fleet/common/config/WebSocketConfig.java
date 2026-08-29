package com.sogeco.fleet.common.config;

import com.sogeco.fleet.common.security.JwtService;
import com.sogeco.fleet.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * Diffusion temps reel en STOMP over SockJS.
 *
 * Rappel de la decision D6 : Socket.io est incompatible avec Spring,
 * qui n'implemente pas son protocole. Le frontend utilisera donc
 * @stomp/stompjs et sockjs-client.
 *
 * Broker simple en memoire : suffisant pour une instance unique et
 * une vingtaine d'utilisateurs.
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;

    @Value("${sogeco.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                .withSockJS();
    }

    /**
     * Authentification a la connexion.
     *
     * Le jeton est transmis dans l'en-tete STOMP CONNECT, pas dans
     * l'URL : une adresse WebSocket se retrouve dans les journaux de
     * proxy, un en-tete non.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {

            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = extractToken(accessor);

                    if (token == null) {
                        throw new IllegalArgumentException("Jeton absent de la trame CONNECT");
                    }

                    UserPrincipal principal = jwtService.parseAccessToken(token)
                            .orElseThrow(() -> new IllegalArgumentException("Jeton invalide"));

                    accessor.setUser(new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities()));

                    log.debug("Connexion WebSocket de {}", principal.getEmail());
                }
                return message;
            }
        });
    }

    private String extractToken(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader("Authorization");
        if (values == null || values.isEmpty()) {
            return null;
        }
        String header = values.get(0);
        return header.startsWith("Bearer ") ? header.substring(7).trim() : header;
    }
}
