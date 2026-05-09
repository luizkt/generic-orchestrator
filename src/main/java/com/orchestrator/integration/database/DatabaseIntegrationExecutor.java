package com.orchestrator.integration.database;

import com.orchestrator.domain.execution.FlowExecutionContext;
import com.orchestrator.domain.model.DatabaseIntegrationConfig;
import com.orchestrator.domain.model.IntegrationDefinition;
import com.orchestrator.domain.model.IntegrationType;
import com.orchestrator.exception.IntegrationExecutionException;
import com.orchestrator.integration.IntegrationExecutor;
import com.orchestrator.service.TemplateResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.BasicUpdate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseIntegrationExecutor implements IntegrationExecutor {

    private final MongoTemplate mongoTemplate;
    private final TemplateResolverService templateResolver;

    @Override
    public IntegrationType getType() { return IntegrationType.DATABASE; }

    @Override
    public Object execute(IntegrationDefinition def, FlowExecutionContext ctx) {
        DatabaseIntegrationConfig db = def.getDatabase();
        if (db == null) throw new IntegrationExecutionException("Config DATABASE ausente: " + def.getId());
        try {
            String collection = db.getColecao();
            log.info("[DATABASE] {} em {}", db.getOperacao(), collection);

            return switch (db.getOperacao()) {
                case INSERT -> {
                    String docJson = templateResolver.resolve(db.getDocumentoTemplate(), ctx);
                    Document doc = Document.parse(docJson);
                    Document inserted = mongoTemplate.insert(doc, collection);
                    yield toMap(inserted);
                }
                case FIND_ONE -> {
                    String filterJson = templateResolver.resolve(db.getFiltroTemplate(), ctx);
                    Document found = mongoTemplate.findOne(new BasicQuery(filterJson), Document.class, collection);
                    yield found != null ? toMap(found) : Map.of();
                }
                case FIND_MANY -> {
                    String filterJson = templateResolver.resolve(db.getFiltroTemplate(), ctx);
                    List<Document> results = mongoTemplate.find(new BasicQuery(filterJson), Document.class, collection);
                    yield Map.of("results", results.stream().map(this::toMap).toList());
                }
                case UPDATE -> {
                    String filterJson = templateResolver.resolve(db.getFiltroTemplate(), ctx);
                    String updateJson = templateResolver.resolve(db.getDocumentoTemplate(), ctx);
                    Query query = new BasicQuery(filterJson);
                    var result = mongoTemplate.updateMulti(query,
                            new BasicUpdate(Document.parse(updateJson)), collection);
                    yield Map.of("matched", result.getMatchedCount(), "modified", result.getModifiedCount());
                }
                case DELETE -> {
                    String filterJson = templateResolver.resolve(db.getFiltroTemplate(), ctx);
                    var result = mongoTemplate.remove(new BasicQuery(filterJson), collection);
                    yield Map.of("deleted", result.getDeletedCount());
                }
            };
        } catch (Exception e) {
            log.error("Erro DATABASE em '{}': {}", def.getId(), e.getMessage());
            throw new IntegrationExecutionException("Falha na integração DATABASE: " + def.getId(), e);
        }
    }

    private Map<String, Object> toMap(Document d) {
        Map<String, Object> m = new HashMap<>();
        for (String k : d.keySet()) {
            Object v = d.get(k);
            m.put(k, v != null ? v.toString() : null);
        }
        return m;
    }
}
