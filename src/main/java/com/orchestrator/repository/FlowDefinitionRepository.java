package com.orchestrator.repository;
import com.orchestrator.domain.model.FlowDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FlowDefinitionRepository extends MongoRepository<FlowDefinition, String> {
    Optional<FlowDefinition> findByFlowIdAndAtivoTrue(String flowId);
    boolean existsByFlowId(String flowId);
}
