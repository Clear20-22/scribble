package com.scribble.game;

import com.scribble.utils.Constants;
import com.scribble.utils.Logger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WordProvider {
    private static final Logger logger = Logger.getLogger(WordProvider.class);

    private final List<String> words;
    private final Random random;

    public WordProvider() {
        this.words = loadWords();
        this.random = new Random();
    }

    public String randomWord() {
        if (words.isEmpty()) {
            return "apple";
        }
        return words.get(random.nextInt(words.size()));
    }

    public List<String> randomChoices(int count) {
        List<String> choices = new ArrayList<>();
        if (words.isEmpty()) {
            return fallbackWords();
        }
        int attempts = 0;
        while (choices.size() < count && attempts < words.size() * 2) {
            String pick = words.get(random.nextInt(words.size()));
            if (!choices.contains(pick)) {
                choices.add(pick);
            }
            attempts++;
        }
        if (choices.isEmpty()) {
            choices.addAll(fallbackWords());
        }
        return choices;
    }

    private List<String> loadWords() {
        List<String> result = new ArrayList<>();
        try (InputStream stream = WordProvider.class.getResourceAsStream(Constants.WORDS_RESOURCE)) {
            if (stream == null) {
                logger.warn("words.txt not found, using fallback words");
                return fallbackWords();
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String word = line.trim();
                    if (!word.isEmpty()) {
                        result.add(word);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load words", e);
            return fallbackWords();
        }
        if (result.size() < 10) {
            result.addAll(fallbackWords());
        }
        return result;
    }

    private List<String> fallbackWords() {
        return List.of("apple", "mountain", "guitar", "ocean", "robot", "castle", "piano", "bridge");
    }
}
