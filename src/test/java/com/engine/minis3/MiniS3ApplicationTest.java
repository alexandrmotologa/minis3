package com.engine.minis3;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class MiniS3ApplicationTest {

    private final PrintStream standardOut = System.out;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    void tearDown() {
        System.setOut(standardOut);
    }

    @Test
    void testVersionDoubleDash() {
        MiniS3Application.main(new String[]{"--version"});
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("minis3 version " + MiniS3Application.VERSION);
    }

    @Test
    void testVersionShortFlag() {
        MiniS3Application.main(new String[]{"-v"});
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("minis3 version " + MiniS3Application.VERSION);
    }

    @Test
    void testVersionSubcommand() {
        MiniS3Application.main(new String[]{"version"});
        assertThat(outputStreamCaptor.toString().trim()).isEqualTo("minis3 version " + MiniS3Application.VERSION);
    }

    @Test
    void testHelpDoubleDash() {
        MiniS3Application.main(new String[]{"--help"});
        assertThat(outputStreamCaptor.toString()).contains("MiniS3 - Lightweight S3-Compatible Content-Addressable Storage Engine");
    }

    @Test
    void testHelpShortFlag() {
        MiniS3Application.main(new String[]{"-h"});
        assertThat(outputStreamCaptor.toString()).contains("Options / Commands:");
    }
}
