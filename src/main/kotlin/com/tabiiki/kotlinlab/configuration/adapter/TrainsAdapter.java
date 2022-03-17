package com.tabiiki.kotlinlab.configuration.adapter;

import com.tabiiki.kotlinlab.configuration.Stations;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "network")
public class TrainsAdapter {

    private List<Stations> trains;

    public void setTrains(List<Stations> trains) {
        this.trains = trains;
    }

    public List<Stations> getTrains(){
        return trains;
    }
}
