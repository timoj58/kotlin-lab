package com.tabiiki.kotlinlab.configuration.adapter;

import com.tabiiki.kotlinlab.configuration.Transport;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "network.transport")
public class TransportAdapter {

    private List<Transport> trains;
    private List<Transport> ferries;
    private List<Transport> cableCars;

    public void setCableCars(List<Transport> cableCars) {
        this.cableCars = cableCars;
    }

    public void setTrains(List<Transport> trains) {
        this.trains = trains;
    }

    public List<Transport> getFerries() {
        return ferries;
    }

    public void setFerries(List<Transport> ferries) {
        this.ferries = ferries;
    }

    public List<Transport> getCableCars() {
        return cableCars;
    }


    public List<Transport> getTrains(){
        return trains;
    }
}
