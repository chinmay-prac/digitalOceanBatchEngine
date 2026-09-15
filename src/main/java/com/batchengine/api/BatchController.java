package com.batchengine.api;

import com.batchengine.model.BatchSnapshot;
import com.batchengine.model.BatchStatus;
import com.batchengine.service.BatchFileParser;
import com.batchengine.service.BatchService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BatchResponses.Submission> submitFile(
            @RequestPart("file") MultipartFile file) {
        return accepted(service.submit(parser.parse(file)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BatchResponses.Submission> submitJson(
            @RequestBody List<String> prompts) {
        return accepted(service.submit(parser.validate(prompts)));
    }

    private ResponseEntity<BatchResponses.Submission> accepted(BatchSnapshot snapshot) {
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
