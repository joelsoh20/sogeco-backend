package com.sogeco.fleet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SogecoFleetApplication {

    public static void main(String[] args) {
        SpringApplication.run(SogecoFleetApplication.class, args);
    }

    /**
     * Toutes les dates sont stockees en UTC ; la conversion vers Africa/Douala
     * se fait a l'affichage. Voir CDC technique, section 5 (conventions).
     */
    @PostConstruct
    void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}
