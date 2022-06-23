package com.tabiiki.kotlinlab.configuration.adapter;

import com.tabiiki.kotlinlab.configuration.LineType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "network.lines")
public class LinesAdapter {

    private Integer defaultLineCapacity;
    private List<String> underground;
    private List<String> overground;
    private List<String> cable;
    private List<String> river;
    private List<String> dockland;
    private List<String> tram;

    public LinesAdapter(
            List<String> underground){
        this.underground = underground;
        this.tram = List.of();
        this.overground = List.of();
        this.cable = List.of();
        this.river = List.of();
        this.dockland = List.of();
    }

    public LinesAdapter(){

    }


    public void setUnderground(List<String> underground) {
        this.underground = underground;
    }

    public void setOverground(List<String> overground) {
        this.overground = overground;
    }

    public void setCable(List<String> cable) {
        this.cable = cable;
    }

    public void setRiver(List<String> river) {
        this.river = river;
    }

    public void setDockland(List<String> dockland) {
        this.dockland = dockland;
    }

    public void setTram(List<String> tram) {
        this.tram = tram;
    }

    public Integer getDefaultLineCapacity() {
        return defaultLineCapacity;
    }

    public void setDefaultLineCapacity(Integer defaultLineCapacity) {
        this.defaultLineCapacity = defaultLineCapacity;
    }

    public Map<LineType, List<String>> getLines() {
        return Map.of(
                LineType.UNDERGROUND, underground,
                LineType.OVERGROUND, overground,
                LineType.CABLE, cable,
                LineType.RIVER, river,
                LineType.DOCKLAND, dockland,
                LineType.TRAM, tram
        );
    }

}
