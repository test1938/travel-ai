package dev.travelpilot.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Offline EmbeddingModel: a deterministic unit vector seeded by the text's hash (same text → same vector). */
public class StubEmbeddingModel implements EmbeddingModel {

    private final int dimensions;

    public StubEmbeddingModel(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> out = new ArrayList<>();
        List<String> texts = request.getInstructions();
        for (int i = 0; i < texts.size(); i++) {
            out.add(new Embedding(vector(texts.get(i)), i));
        }
        return new EmbeddingResponse(out);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    float[] vector(String text) {
        Random random = new Random(text == null ? 0 : text.hashCode());
        float[] v = new float[dimensions];
        double norm = 0;
        for (int i = 0; i < dimensions; i++) {
            v[i] = (float) random.nextGaussian();
            norm += v[i] * v[i];
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < dimensions; i++) {
            v[i] *= scale;
        }
        return v;
    }
}
