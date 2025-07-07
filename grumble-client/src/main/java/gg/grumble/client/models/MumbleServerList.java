package gg.grumble.client.models;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.util.List;

@JacksonXmlRootElement(localName = "servers")
public class MumbleServerList {
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "server")
    private List<MumbleServer> server;

    public List<MumbleServer> getServer() { return server; }
    public void setServer(List<MumbleServer> server) { this.server = server; }
}
