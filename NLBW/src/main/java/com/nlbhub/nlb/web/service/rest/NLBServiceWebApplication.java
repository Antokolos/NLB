package com.nlbhub.nlb.web.service.rest;

import org.glassfish.jersey.server.ResourceConfig;

public class NLBServiceWebApplication extends ResourceConfig {
    public NLBServiceWebApplication() {
        // Register resource class
        //register(GetNLBDataService.class);
        // Alternatively, register whole package
        packages("com.nlbhub.nlb.web.service.rest");
    }
}
