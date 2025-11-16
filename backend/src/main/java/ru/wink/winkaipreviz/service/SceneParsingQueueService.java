package ru.wink.winkaipreviz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory bounded queue for scene parsing tasks.
 * First implementation is intentionally simple and local; can be replaced with Redis/RabbitMQ later.
 */
@Service
public class SceneParsingQueueService {

    private static final Logger log = LoggerFactory.getLogger(SceneParsingQueueService.class);

    private final BlockingQueue<SceneParsingTask> queue;

    public SceneParsingQueueService(
            @Value("${app.scene-parser.queue-capacity:256}") int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
        log.info("Initialized SceneParsingQueueService with capacity={}", capacity);
    }

    /**
     * Non-blocking attempt to submit a new task.
     * Returns false if the queue is full; caller may decide to retry or mark scene as FAILED.
     */
    public boolean submit(SceneParsingTask task) {
        boolean offered = queue.offer(task);
        if (!offered) {
            log.warn("Scene parsing queue is full; task rejected. scriptId={}, sceneId={}",
                    task.getScriptId(), task.getSceneId());
        }
        return offered;
    }

    /**
     * Blocking retrieval of the next task for worker processing.
     */
    public SceneParsingTask take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }
}


