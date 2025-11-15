package ru.wink.winkaipreviz.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
		log.warn("Bad request: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of(
						"error", "Bad Request",
						"message", ex.getMessage()
				));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> handleMaxUpload(MaxUploadSizeExceededException ex) {
		log.warn("File too large: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
				.body(Map.of(
					"error", "Payload Too Large",
					"message", "Файл слишком большой. Максимальный размер 50MB"
				));
	}

	@ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
		// Игнорируем ошибки поиска статических ресурсов - это нормально для API-only backend
		log.debug("Static resource not found (expected for API-only backend): {}", ex.getResourcePath());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(Map.of(
						"error", "Not Found",
						"message", "Resource not found"
				));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of(
						"error", "Internal Server Error",
						"message", "Произошла внутренняя ошибка сервера: " + ex.getMessage()
				));
	}
}


