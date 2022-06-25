package com.tabiiki.kotlinlab.configuration.adapter;

import com.tabiiki.kotlinlab.configuration.TransportConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "network.transporters")
public class TransportersAdapter {

    private List<TransportConfig> trains;
    private List<TransportConfig> ferries;
    private List<TransportConfig> cableCars;

    public TransportersAdapter(List<TransportConfig> trains) {
        this.trains = trains;
        this.ferries = List.of();
        this.cableCars = List.of();
    }

    public TransportersAdapter() {

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

    public void setCableCars(List<TransportConfig> cableCars) {
        this.cableCars = cableCars;
    }

    public List<TransportConfig> getTrains() {
        return trains;
    }

    public void setTrains(List<TransportConfig> trains) {
        this.trains = trains;
    }
}
