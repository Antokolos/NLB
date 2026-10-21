module NLBW {
    requires java.logging;

    requires transitive java.scripting;
    requires transitive java.xml;

    requires NLBL;

    requires org.eclipse.jetty.server;
    requires org.eclipse.jetty.ee10.servlet;
    requires jakarta.ws.rs;
    requires jersey.common;
    requires jersey.server;
    requires jersey.container.servlet.core;
    requires jakarta.servlet;
    requires com.fasterxml.jackson.databind;

    exports com.nlbhub.nlb.web;
    exports com.nlbhub.nlb.web.exception;
    exports com.nlbhub.nlb.web.service.rest;
    exports com.nlbhub.nlb.web.player;

}
