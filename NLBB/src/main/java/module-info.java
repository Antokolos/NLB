module NLBB {
    requires transitive java.desktop;

    requires NLBL;
    requires NLBW;

    requires org.slf4j;
    requires piccolo2d.core;
    requires piccolo2d.extras;
    requires swingx;
    requires org.jetbrains.annotations;

    exports com.camick.swing.layout;
    exports com.nlbhub.nlb.builder;
    exports com.nlbhub.nlb.builder.config;
    exports com.nlbhub.nlb.builder.form;
    exports com.nlbhub.nlb.builder.model;
    exports com.nlbhub.nlb.builder.util;
    exports com.nlbhub.nlb.builder.view;

}
