package com.owlynbackend.services;



import com.owlynbackend.internal.errors.DocumentExtractionException;
import org.apache .tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
public class DocumentExtractionService {

    // Tika is thread-safe, we can reuse one instance
    private final Tika tika = new Tika();

    public String extractTextFromFile(MultipartFile file) {
        if (file.isEmpty()) {
            return "";
        }

        try (InputStream stream = file.getInputStream()) {
            // Tika automatically detects the file type (PDF, DOCX) and extracts text!
            String extractedText = tika.parseToString(stream);

            // Clean up any weird invisible characters or excessive newlines from PDFs
            return extractedText.replaceAll("[\\r\\n]+", "\n").trim();

        } catch (Exception e) {
            // We throw our custom exception so the GlobalExceptionHandler catches it cleanly
            throw new DocumentExtractionException(
                    "Failed to extract text from file: " + file.getOriginalFilename() + ". Ensure it is a valid PDF or DOCX.", e
            );
        }
    }
}