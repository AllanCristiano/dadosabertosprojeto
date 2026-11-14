package com.anp.fuel.alerta;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelogioConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
}
