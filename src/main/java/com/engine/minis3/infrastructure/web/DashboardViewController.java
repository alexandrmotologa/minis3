package com.engine.minis3.infrastructure.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardViewController {

    @GetMapping(value = {"/", "/index.html"}, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/index.html"));
    }

    @GetMapping(value = "/styles.css", produces = "text/css")
    public ResponseEntity<Resource> styles() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/css"))
                .body(new ClassPathResource("static/styles.css"));
    }

    @GetMapping(value = "/app.js", produces = "application/javascript")
    public ResponseEntity<Resource> app() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript"))
                .body(new ClassPathResource("static/app.js"));
    }

    @GetMapping(value = "/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }
}
