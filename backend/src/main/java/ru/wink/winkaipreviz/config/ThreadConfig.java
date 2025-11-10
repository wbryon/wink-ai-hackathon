package ru.wink.winkaipreviz.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ThreadConfig {

	@Bean
	public AsyncTaskExecutor virtualThreadExecutor() {
		ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
		return new ConcurrentTaskExecutor(executor);
	}
}


