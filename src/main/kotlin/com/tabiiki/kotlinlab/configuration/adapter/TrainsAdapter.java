package com.tabiiki.kotlinlab.configuration.adapter;

import com.tabiiki.kotlinlab.configuration.Trains;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "network.vehicles")
public class TrainsAdapter {

    private List<Trains> trains;

    public void setTrains(List<Trains> trains) {
        this.trains = trains;
    }

    public List<Trains> getTrains(){
        return trains;
    }
}
