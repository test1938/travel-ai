package dev.travelpilot.security;

import dev.travelpilot.common.config.TravelPilotProperties;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Guest authentication for the API. A WebMvcConfigurer, so @WebMvcTest slices get the same rules as the app.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TravelPilotProperties.class)
public class SecurityConfig implements WebMvcConfigurer {

    private final GuestTokenService tokens;

    public SecurityConfig(TravelPilotProperties props, ObjectProvider<Clock> clock) {
        this.tokens = new GuestTokenService(props.security(), clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    GuestTokenService guestTokenService() {
        return tokens;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new GuestAuthInterceptor(tokens)).addPathPatterns("/api/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new GuestArgumentResolver());
    }
}
