# Pontos de melhorias

1. Objetivo: Criar uma classe responsavel por gerenciar um conjunto de configuracões em lista de kafka e rabbitmq, com essa alteraćão sera necessario mudar o formato do arquivo que configuraćão do worflows para aceitar o novo formato que melhor se adequa ao novo modelo de multiconfiugraćão de integraćões de kafkas e rabbitmqs.

Modelo Novo de confiugraćão application.yml:

``` yaml
orch-integrations:
  rabbitmqs:
    - id: rabbitmq-notifier
      host: ${RABBITMQ_HOST:localhost}
      port: ${RABBITMQ_PORT:5672}
      username: ${RABBITMQ_USERNAME:guest}
      password: ${RABBITMQ_PASSWORD:guest} 
  kafkas:
    - id: kafka-user-tracking
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS_1:localhost:9092}
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.apache.kafka.common.serialization.StringSerializer
    - id: notificar-consulta
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS_2:localhost:9093}
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

Na nova alteraćão do YAML de configuraćão de workflow devemos alterar o provider para ficar no nivel principal. O match para sabermos qual integraćão utilizar é pelo campo `id` do arquivo de configruaćão de workflow YAML e pelo arquivo application.yml do orquestrador

``` yaml
    - id: "notificar-consulta"
      ordem: 2
      tipo: QUEUE
      provider: KAFKA
      continuarEmErro: true
      queue:
        topic: "consultas-cursos-clientes"
        mensagemTemplate: |
          {"documento":"{{contrato.aluno.documento}}"}
```


2. monte um profile spring da aplicaćão com nome docker com as configuraćões para executar o docker compose criado

3. Como complemento do docker-compose, vamos criar uma estrutura para testes, para isso vamos criar uma pasta na raiz do projeto chamada mongodb-workflows, nesta pasta vamos criar um script que deve ser executado no container criado pelo docker-compose que deve configurar uma base de dados chamada generic-orchestrator, depois vamos criar a collection workflows.

4. o ponto de melhoria 3 vamos precisar validar se as configuraćões que temos hoje atendem as configuraćões realizadas. Caso houver divergencias realize implementaćões para se adapatar ao ponto de melhoria 3