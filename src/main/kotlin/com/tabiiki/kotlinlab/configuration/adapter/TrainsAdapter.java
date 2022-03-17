package com.tabiiki.kotlinlab.configuration.adapter;

import com.tabiiki.kotlinlab.configuration.TrainConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "network")
public class TrainsAdapter {

    private List<TrainConfig> trains;

    public void setTrains(List<TrainConfig> trains) {
        this.trains = trains;
    }

    public List<TrainConfig> getTrains(){
        return trains;
    }
}
