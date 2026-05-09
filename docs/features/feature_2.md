# Pontos de melhorias

1. Temos que atualizar a nossa API de workflows para utilizar a versão no caminho da URL

``` java
@PostMapping("/{version}/{flowId}")
public ResponseEntity<OrchestrationResponse> orchestrate(
  @PathVariable String flowId,
  @RequestBody Map<String, Object> payload)
```

com isso a busca pelo workflows no mongo deve ser guiada utilizando os campos do arquivo de configuraćão configuraćão e da API HTTP seguindo a tabela 1

Tabela 1
|----------------|-------------------------------------------|
| Campo API HTTP | Campo arquivo de configuraćão de workflow |
| version        | versao                                    |
| flowId         | id                                        |

Passos obrigatorios:

- Atualizar README.md
- Realizar testes unitários para testar os novos cenarios, caso necessario atualize os testes que ja existem

2. Com essa altećão 1 vamos precisar atualizar os indices do mongodb, atualize o script de inicializaćão do container do mongodb definido no docker-compose para que crie um indice utilizando o `id` e a `versao`, pois os workdflows serão pesquisados utilizando esses 2 campos agora.
