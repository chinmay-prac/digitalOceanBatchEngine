package com.batchengine.service;

import com.batchengine.config.InferenceProperties;
import com.batchengine.exception.InvalidBatchFileException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class BatchFileParser {

    private final InferenceProperties properties;

    public BatchFileParser(InferenceProperties properties) {
        this.properties = properties;
    }

    public List<String> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidBatchFileException("A non-empty file is required");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new InvalidBatchFileException("Only .txt files are accepted");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> prompts = reader.lines().toList();
            if (prompts.isEmpty()) {
                throw new InvalidBatchFileException("The file must contain at least one prompt");
            }
            if (prompts.size() > properties.getMaxBatchSize()) {
                throw new InvalidBatchFileException(
                        "The file exceeds the maximum batch size of "
                                + properties.getMaxBatchSize());
            }
            if (prompts.stream().anyMatch(String::isBlank)) {
                throw new InvalidBatchFileException("Every line must contain a non-blank prompt");
            }
            return prompts;
        } catch (IOException exception) {
            throw new InvalidBatchFileException("The uploaded file could not be read");
        }
    }
}
