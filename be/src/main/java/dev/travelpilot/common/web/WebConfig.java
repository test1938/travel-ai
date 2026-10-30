package dev.travelpilot.common.web;

import dev.travelpilot.common.domain.PlaceTag;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Lets query parameters use dataset tag codes, e.g. {@code ?tag=THEME-PARK}. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, PlaceTag.class, PlaceTag::fromCode);
    }
}
