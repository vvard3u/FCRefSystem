package ru.fcref.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FcRefSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(FcRefSystemApplication.class, args);
    }
}
