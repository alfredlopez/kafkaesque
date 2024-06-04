package com.asanasoft.common.handlers;

import com.asanasoft.common.Application;
import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConnectProxyHandler {
    private Logger logger = LoggerFactory.getLogger(KafkaConnectProxyHandler.class);
    private WebClient    proxyClient;

    public WebClient getProxyClient() {
        if (proxyClient == null) {
            proxyClient = WebClient.create(Application.globalVertx);
        }
        return proxyClient;
    }

    public void handle(RoutingContext routingContext) {
        logger.debug("Proxying request...");

        int port = routingContext.get("port");

        logger.debug("...to port " + port);

        try {
            JsonObject body = routingContext.getBodyAsJson();

            if (body != null) {
                logger.debug("...with body...");
                getProxyClient().request(routingContext.request().method(), port, "localhost", routingContext.request().uri()).sendJsonObject(body, c_res -> {
                    handleProxyResponse(routingContext, c_res.result());
                });
            }
            else {
                logger.debug("...without body...");
                getProxyClient().request(routingContext.request().method(), port, "localhost", routingContext.request().uri()).send(c_res -> {
                    handleProxyResponse(routingContext, c_res.result());
                });
            }
        } catch (Exception e) {
            logger.error("An error occurred proxying...", e);
        }
    }

    protected void handleProxyResponse(RoutingContext routingContext, HttpResponse<Buffer> response) {
        logger.debug("Proxying response: " + response.statusCode());
        routingContext.response().setChunked(true);
        routingContext.response().setStatusCode(response.statusCode());
        routingContext.response().headers().setAll(response.headers());
        Buffer data = response.body();
        logger.debug("Proxying response body: " + data.toString("ISO-8859-1"));
        routingContext.response().write(data);
        routingContext.response().end();
    }
}
