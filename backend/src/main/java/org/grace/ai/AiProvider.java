package org.grace.ai;

import java.util.List;

public interface AiProvider {
    String generate(AiConfiguration configuration, String systemPrompt, String userPrompt) throws Exception;
    List<List<Double>> embed(AiConfiguration configuration, List<String> inputs) throws Exception;
}
