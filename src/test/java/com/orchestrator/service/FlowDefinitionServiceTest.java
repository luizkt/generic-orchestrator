package com.orchestrator.service;

import com.orchestrator.domain.model.FlowDefinition;
import com.orchestrator.exception.FlowNotFoundException;
import com.orchestrator.repository.FlowDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlowDefinitionServiceTest {

    @Mock private FlowDefinitionRepository repository;
    @Mock private YamlParserService yamlParserService;
    @InjectMocks private FlowDefinitionService service;

    private FlowDefinition sample;

    @BeforeEach
    void setUp() {
        sample = new FlowDefinition();
        sample.setFlowId("test-flow");
        sample.setAtivo(true);
        sample.setVersao("1.0.0");
    }

    @Test @DisplayName("Salva fluxo a partir de YAML")
    void deveSalvarFluxo() {
        when(yamlParserService.parse(anyString())).thenReturn(sample);
        when(repository.save(any(FlowDefinition.class))).thenAnswer(i -> i.getArgument(0));

        FlowDefinition saved = service.save("yaml content");

        assertThat(saved.getFlowId()).isEqualTo("test-flow");
        assertThat(saved.getCriadoEm()).isNotNull();
        verify(repository).save(any(FlowDefinition.class));
    }

    @Test @DisplayName("Busca fluxo ativo por id")
    void deveBuscarFluxoAtivo() {
        when(repository.findByFlowIdAndAtivoTrue("test-flow")).thenReturn(Optional.of(sample));
        assertThat(service.findActiveByFlowId("test-flow")).isEqualTo(sample);
    }

    @Test @DisplayName("Lança exceção quando fluxo não encontrado")
    void deveLancarQuandoNaoEncontrado() {
        when(repository.findByFlowIdAndAtivoTrue("inexistente")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findActiveByFlowId("inexistente"))
                .isInstanceOf(FlowNotFoundException.class);
    }

    @Test @DisplayName("Desativa fluxo")
    void deveDesativarFluxo() {
        when(repository.findByFlowIdAndAtivoTrue("test-flow")).thenReturn(Optional.of(sample));
        service.deactivate("test-flow");
        assertThat(sample.isAtivo()).isFalse();
        verify(repository).save(sample);
    }
}
