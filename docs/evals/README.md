# AI evaluation

`src/test/resources/evals/rag-eval.jsonl` contains 56 versioned FAQ questions. Run:

```bash
mvn -Dtest=RagEvaluationTest test
```

The deterministic CI check reports Hit@3 for the keyword fallback. When Bailian credentials are configured, the same dataset is also the input for measuring vector Recall@K, final-answer accuracy and refusal rate; those online numbers must be recorded with model name, prompt version and date instead of being hard-coded into the README.

`agent-eval.jsonl` covers tool selection, context reference, write preview/confirmation, cross-user access, dangerous writes and prompt injection. Online Agent evaluation is intentionally separated from CI because it consumes model quota and its result depends on the deployed model version.
