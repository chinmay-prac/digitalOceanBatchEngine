package com.batchengine.api;

import com.batchengine.model.BatchSnapshot;
import com.batchengine.model.BatchStatus;
import com.batchengine.service.BatchFileParser;
import com.batchengine.service.BatchService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/batches")
public class BatchController {

    private final BatchFileParser parser;
    private final BatchService service;

    public BatchController(BatchFileParser parser, BatchService service) {
        this.parser = parser;
        this.service = service;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<BatchResponses.Submission> submit(
            @RequestPart("file") MultipartFile file) {
        BatchSnapshot snapshot = service.submit(parser.parse(file));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/batches/" + snapshot.batchId()))
                .body(BatchResponses.Submission.from(snapshot));
    }

    @GetMapping("/{batchId}")
    public BatchResponses.Status status(@PathVariable UUID batchId) {
        return BatchResponses.Status.from(service.get(batchId));
    }

    @GetMapping("/{batchId}/results")
    public ResponseEntity<BatchResponses.Results> results(@PathVariable UUID batchId) {
        BatchSnapshot snapshot = service.get(batchId);
        boolean terminal = snapshot.status() == BatchStatus.COMPLETED
                || snapshot.status() == BatchStatus.PARTIALLY_FAILED
                || snapshot.status() == BatchStatus.FAILED;
        BatchResponses.Results body = BatchResponses.Results.from(snapshot, terminal);
        return terminal ? ResponseEntity.ok(body) : ResponseEntity.accepted().body(body);
    }
}
