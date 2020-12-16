package com.reactnativegooglecloudspeechtotext.speech_service;

import com.google.auth.oauth2.GoogleCredentials;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Authenticates the gRPC channel using the specified {@link GoogleCredentials}.
 */
public class GoogleCredentialsInterceptor implements ClientInterceptor {

    private final String apiKey;

    GoogleCredentialsInterceptor(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(final MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               final Channel next) {
        return new ClientInterceptors
                .CheckedForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
            @Override
            protected void checkedStart(Listener<RespT> responseListener,
                                        Metadata headers) {
                Metadata newHeaders = new Metadata();
                Metadata.Key<String> headerKey = Metadata.Key.of("X-Goog-Api-Key", Metadata.ASCII_STRING_MARSHALLER);
                newHeaders.put(headerKey, apiKey);
                delegate().start(responseListener, newHeaders);
            }
        };
    }
}
