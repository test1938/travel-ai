package dev.travelpilot.security;

import dev.travelpilot.common.config.TravelPilotProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
 * Guest authentication, rate limits and the body size limit for the API. A WebMvcConfigurer, so @WebMvcTest slices get the same
 * rules as the app.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TravelPilotProperties.class)
public class SecurityConfig implements WebMvcConfigurer {

    private final GuestTokenService tokens;
    private final RateLimiter limiter;
    private final int maxBodyBytes;
    private final MeterRegistry meters;

    /** Test slices may have no MeterRegistry; the counters then go to a local one. */
    public SecurityConfig(TravelPilotProperties props, ObjectProvider<Clock> clock,
            ObjectProvider<MeterRegistry> meters) {
        this.tokens = new GuestTokenService(props.security(), clock.getIfAvailable(Clock::systemUTC));
        this.limiter = new RateLimiter(props.security().rateLimit());
        this.maxBodyBytes = props.security().maxBodyBytes();
        this.meters = meters.getIfAvailable(SimpleMeterRegistry::new);
    }

    @Bean
    SecurityHeadersFilter securityHeadersFilter() {
        return new SecurityHeadersFilter();
    }

    @Bean
    BodySizeLimitFilter bodySizeLimitFilter() {
        return new BodySizeLimitFilter(maxBodyBytes);
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
        registry.addInterceptor(new RateLimitInterceptor(limiter, meters)).addPathPatterns("/api/**").order(1);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new GuestArgumentResolver());
    }
}
