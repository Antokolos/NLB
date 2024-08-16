package com.nlbhub.nlb.web.service.rest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CustomExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        // TODO: Can format beautiful-looking error page here
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("Error: " + exception.getMessage())
                .type(MediaType.TEXT_PLAIN)
                .build();
    }
}

