package ru.wink.winkaipreviz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Starts and manages a small pool of background worker threads that
 * continuously poll the in-memory scene parsing queue and process tasks.
 */
@Component
public class SceneParsingWorkerManager {

    private static final Logger log = LoggerFactory.getLogger(SceneParsingWorkerManager.class);

    private final SceneParsingQueueService queueService;
    private final SceneParsingWorkerService workerService;
    private final int workerThreads;

    public SceneParsingWorkerManager(SceneParsingQueueService queueService,
                                     SceneParsingWorkerService workerService,
                                     @Value("${app.scene-parser.worker-threads:4}") int workerThreads) {
        this.queueService = queueService;
        this.workerService = workerService;
        this.workerThreads = Math.max(1, workerThreads);
    }

    @PostConstruct
    public void startWorkers() {
        log.info("Starting {} scene parser worker threads", workerThreads);
        for (int i = 0; i < workerThreads; i++) {
            int index = i + 1;
            Thread.ofVirtual()
                    .name("scene-parser-worker-" + index)
                    .start(() -> runWorkerLoop(index));
        }
    }

    private void runWorkerLoop(int index) {
        log.info("Scene parser worker {} started", index);
        while (true) {
            try {
                SceneParsingTask task = queueService.take();
                workerService.processTask(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Scene parser worker {} interrupted, stopping.", index);
                break;
            } catch (Exception e) {
                log.error("Unexpected error in scene parser worker {}: {}", index, e.getMessage(), e);
            }
        }
    }
}


