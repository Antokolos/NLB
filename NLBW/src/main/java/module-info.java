module NLBW {
    requires java.logging;

    requires transitive java.scripting;
    requires transitive java.xml;

    requires NLBL;

    requires org.eclipse.jetty.server;
    requires org.eclipse.jetty.servlet;
    requires jakarta.ws.rs;
    requires jersey.common;
    requires jersey.server;
    requires jersey.container.servlet.core;

    exports com.nlbhub.nlb.web;
    exports com.nlbhub.nlb.web.exception;
    exports com.nlbhub.nlb.web.service.rest;

}
