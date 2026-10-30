package dev.travelpilot.common.config;

import dev.travelpilot.common.domain.Destination;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One injectable clock in Vietnam time, so "today" is testable. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.system(Destination.ZONE);
    }
}
