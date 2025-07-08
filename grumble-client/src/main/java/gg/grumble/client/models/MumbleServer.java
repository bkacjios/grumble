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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCa() {
        return ca;
    }

    public void setCa(int ca) {
        this.ca = ca;
    }

    public String getContinentCode() {
        return continentCode;
    }

    public void setContinentCode(String continentCode) {
        this.continentCode = continentCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
