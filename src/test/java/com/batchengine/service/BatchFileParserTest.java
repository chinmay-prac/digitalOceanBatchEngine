package com.batchengine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batchengine.config.InferenceProperties;
import com.batchengine.exception.InvalidBatchFileException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class BatchFileParserTest {

    private final InferenceProperties properties = new InferenceProperties();
    private final BatchFileParser parser = new BatchFileParser(properties);

    @Test
    void parsesOnePromptPerLine() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "prompts.txt", "text/plain", "one\ntwo\nthree\n".getBytes());

        assertThat(parser.parse(file)).containsExactly("one", "two", "three");
    }

    @Test
    void rejectsBlankLinesAndWrongExtension() {
        MockMultipartFile blankLine = new MockMultipartFile(
                "file", "prompts.txt", "text/plain", "one\n \ntwo".getBytes());
        MockMultipartFile wrongExtension = new MockMultipartFile(
                "file", "prompts.csv", "text/plain", "one".getBytes());

        assertThatThrownBy(() -> parser.parse(blankLine))
                .isInstanceOf(InvalidBatchFileException.class);
        assertThatThrownBy(() -> parser.parse(wrongExtension))
                .isInstanceOf(InvalidBatchFileException.class);
    }

    @Test
    void rejectsOversizedBatch() {
        properties.setMaxBatchSize(2);
        MockMultipartFile file = new MockMultipartFile(
                "file", "prompts.txt", "text/plain", "one\ntwo\nthree".getBytes());

        assertThatThrownBy(() -> parser.parse(file))
                .isInstanceOf(InvalidBatchFileException.class)
                .hasMessageContaining("maximum batch size");
    }
}
