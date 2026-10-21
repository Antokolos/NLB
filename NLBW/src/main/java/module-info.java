module NLBW {
    requires java.logging;

    requires transitive java.scripting;
    requires transitive java.xml;

    requires NLBL;

    requires org.eclipse.jetty.server;
    requires org.eclipse.jetty.ee10.servlet;
    requires jakarta.servlet;
    requires com.fasterxml.jackson.databind;

    exports com.nlbhub.nlb.web;
    exports com.nlbhub.nlb.web.exception;
    exports com.nlbhub.nlb.web.player;

}
