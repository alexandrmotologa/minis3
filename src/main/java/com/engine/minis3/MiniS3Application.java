package com.engine.minis3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MiniS3Application {

    public static final String VERSION = "1.0.0";

    public static void main(String[] args) {
        if (args.length > 0) {
            String firstArg = args[0].trim().toLowerCase();
            if (firstArg.equals("--version") || firstArg.equals("-v") || firstArg.equals("version")) {
                System.out.println("minis3 version " + VERSION);
                return;
            }
            if (firstArg.equals("--help") || firstArg.equals("-h") || firstArg.equals("help")) {
                printHelp();
                return;
            }
        }
        SpringApplication.run(MiniS3Application.class, args);
    }

    private static void printHelp() {
        System.out.println("MiniS3 - Lightweight S3-Compatible Content-Addressable Storage Engine");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar minis3.jar [options]");
        System.out.println();
        System.out.println("Options / Commands:");
        System.out.println("  --version, -v, version    Print version information and exit");
        System.out.println("  --help, -h, help          Print this help message and exit");
    }
}
