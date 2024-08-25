module NLBL {
    requires java.logging;

    requires transitive java.desktop;
    requires transitive java.scripting;
    requires transitive java.xml;

    requires org.jetbrains.annotations;
    requires jakarta.xml.bind;
    requires org.slf4j;
    requires itextpdf;
    requires org.mozilla.rhino;
    requires org.eclipse.jgit;

    exports com.nlbhub.nlb.api;
    exports com.nlbhub.nlb.api.config;
    exports com.nlbhub.nlb.domain;
    exports com.nlbhub.nlb.domain.export;
    exports com.nlbhub.nlb.domain.export.hypertext;
    exports com.nlbhub.nlb.domain.export.hypertext.document;
    exports com.nlbhub.nlb.domain.export.xml;
    exports com.nlbhub.nlb.domain.export.xml.beans.jsiq2;
    exports com.nlbhub.nlb.exception;
    exports com.nlbhub.nlb.util;
    exports com.nlbhub.nlb.vcs;
    exports com.nlbhub.user.domain;
    exports name.fraser.neil.plaintext;

    opens com.nlbhub.nlb.api.config to jakarta.xml.bind;
    opens com.nlbhub.nlb.domain to jakarta.xml.bind;
    opens com.nlbhub.user.domain to jakarta.xml.bind;
}
