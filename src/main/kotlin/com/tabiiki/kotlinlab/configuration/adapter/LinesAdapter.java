package com.tabiiki.kotlinlab.configuration.adapter;

import com.tabiiki.kotlinlab.enumerator.LineType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "network.lines")
public class LinesAdapter {
    private List<String> underground;
    private List<String> overground;
    private List<String> cable;
    private List<String> river;

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

    public Map<LineType, List<String>> getLines() {
        return Map.of(
                LineType.UNDERGROUND, underground,
                LineType.OVERGROUND, overground,
                LineType.CABLE, cable,
                LineType.RIVER, river
        );
    }

}
