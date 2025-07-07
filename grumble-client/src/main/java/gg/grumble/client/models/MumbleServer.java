package gg.grumble.client.models;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class MumbleServer {
    @JacksonXmlProperty(isAttribute = true, localName = "name")
    private String name;

    @JacksonXmlProperty(isAttribute = true)
    private int ca;

    @JacksonXmlProperty(isAttribute = true, localName = "continent_code")
    private String continentCode;

    @JacksonXmlProperty(isAttribute = true, localName = "country")
    private String country;

    @JacksonXmlProperty(isAttribute = true, localName = "country_code")
    private String countryCode;

    @JacksonXmlProperty(isAttribute = true)
    private String ip;

    @JacksonXmlProperty(isAttribute = true)
    private int port;

    @JacksonXmlProperty(isAttribute = true)
    private String region;

    @JacksonXmlProperty(isAttribute = true)
    private String url;

    // + getters and setters
}
