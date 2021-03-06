package weblog.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/event-stream").setAllowedOrigins("*").withSockJS();		// Used to provide a stream of logging events (e.g. new contact logged) 
        registry.addEndpoint("/rigctl-update").setAllowedOrigins("*").withSockJS();		// Used to send rig updates to weblog (e.g. new frequency, mode etc.)
        registry.addEndpoint("/dx-spots").setAllowedOrigins("*").withSockJS();			// DX Cluster spots
    }

}