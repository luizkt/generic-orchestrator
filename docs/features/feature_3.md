# Pontos de melhorias

1. Vamos dar mais resiliencia para a nossa aplicaćão, para isso vamos implementar meios de retry, circuit break para as integraćoes HTTP que temos disponiveis e tornar as nossas orquestraćões com maior resiliencia e taxa de sucesso. Para isso utilize as melhores praticas. Para ficar facil manuentenćão caso seja necessario mudar os parametros de retry e circuit break externalize os parametros de configuraćão para o application.yml. Devemos ter uma configuraćão padrão para o retry e o circuit breaker das integraćões HTTP.

Vamos criar as configuraćões do application.yml dessa forma:

``` yaml
orch-integrations:
  retry-configuration:
    maximum-attempts: 3
    delay: 100
    backoff-multiplier: 2.0
    maximum-delay: 10000
    retryable-http-condition:
      - 500
      - 429
      - 408
    timeout: 1000
  circuit-breaker-configuration:
    sliding-window-size: 10
    sliding-window-size-type: COUNT | TIME
    minimum-number-calls: 100
    failure-rate-threshold: 50
    wait-duration-open-state: 30
    permited-calls-open-state: 10
    automatic-transition-half-open-enabled: true
    slow-call-duration-threshold: 800
```

## Utilize essas definićões para entender melhor os campos de retry
- Maximum Attempts: The total number of times an operation will be tried before giving up.Initial Delay: The wait time before the very first retry.Backoff Multiplier: The coefficient used to increase the delay between subsequent retries. For exponential backoff, this is usually \(2.0\).
- Initial Delay: The wait time before the very first retry.
- Backoff Multiplier: The coefficient used to increase the delay between subsequent retries. For exponential backoff, this is usually \(2.0\)
- Maximum Delay: The cap on how long the system will wait between retries, ensuring delays do not grow infinitely.
- Maximum Delay: The cap on how long the system will wait between retries, ensuring delays do not grow infinitely.
- Timeout: The maximum time allotted for an individual attempt or the total allowable budget for the whole retry operation.

## Utilize essas definićões para entender melhor os campos de circuit breaker

📊 Metric & Evaluation Settings
- Sliding Window Size: The number of recent calls (count-based) or time-bound seconds (time-based) the breaker tracks to evaluate system health.
- Minimum Number of Calls: The minimum requests required before the circuit breaker will even begin calculating failure rates.
- Failure Rate Threshold: The percentage of failed calls (e.g., \(50\%\)

⏱️ Timeout & State Settings
- Wait Duration in Open State: The amount of time the circuit remains blocked (Open) before transitioning to the Half-Open state to test the downstream service.
- Permitted Calls in Half-Open State: The number of test requests allowed to pass through to the downstream service to verify if it has recovered.

🛠️ Advanced Settings
- Automatic Transition to Half-Open Enabled: Determines if the circuit should automatically transition to Half-Open after the wait duration, or wait for the next call to trigger it.
- Slow Call Duration Threshold: Max duration before a call is deemed "slow" (e.g., \(2000\) and counted towards the failure rate
