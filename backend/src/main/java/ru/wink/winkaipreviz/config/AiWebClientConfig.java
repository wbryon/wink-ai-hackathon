package ru.wink.winkaipreviz.config;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.Objects;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;

@Configuration
public class AiWebClientConfig {

    @Bean
    public WebClient aiWebClient(@Value("${ai.parser.base-url}") String baseUrl) {
        var httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(Objects.requireNonNull(baseUrl, "ai.parser.base-url must not be null"))
                .clientConnector(new ReactorClientHttpConnector(Objects.requireNonNull(httpClient)))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "wink-previz-ai-client/1.0")
                .filter((request, next) -> next.exchange(request)
                        .flatMap(response -> {
                            if (response.statusCode().isError()) {
                                return response.createException().flatMap(Mono::error);
                            }
                            return Mono.just(response);
                        }))
                .build();
    }
}
