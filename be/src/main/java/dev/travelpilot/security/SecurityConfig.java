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
 * Guest authentication and rate limits for the API. A WebMvcConfigurer, so @WebMvcTest slices get the same
 * rules as the app.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TravelPilotProperties.class)
public class SecurityConfig implements WebMvcConfigurer {

    private final GuestTokenService tokens;
    private final RateLimiter limiter;

    public SecurityConfig(TravelPilotProperties props, ObjectProvider<Clock> clock) {
        this.tokens = new GuestTokenService(props.security(), clock.getIfAvailable(Clock::systemUTC));
        this.limiter = new RateLimiter(props.security().rateLimit());
    }

    @Bean
    GuestTokenService guestTokenService() {
        return tokens;
    }

    @Bean
    RateLimiter rateLimiter() {
        return limiter;
    }

    /** Order matters: the rate limit needs the verified guest. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new GuestAuthInterceptor(tokens)).addPathPatterns("/api/**").order(0);
        registry.addInterceptor(new RateLimitInterceptor(limiter)).addPathPatterns("/api/**").order(1);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new GuestArgumentResolver());
    }
}
