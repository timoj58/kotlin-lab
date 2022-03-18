package com.tabiiki.kotlinlab.configuration.adapter;

import com.tabiiki.kotlinlab.configuration.TransportConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "network.transport")
public class TransportAdapter {

    private List<TransportConfig> trains;
    private List<TransportConfig> ferries;
    private List<TransportConfig> cableCars;

    public void setCableCars(List<TransportConfig> cableCars) {
        this.cableCars = cableCars;
    }

    public void setTrains(List<TransportConfig> trains) {
        this.trains = trains;
    }

    public List<TransportConfig> getFerries() {
        return ferries;
    }

    public void setFerries(List<TransportConfig> ferries) {
        this.ferries = ferries;
    }

    public List<TransportConfig> getCableCars() {
        return cableCars;
    }


    public List<TransportConfig> getTrains(){
        return trains;
    }
}
