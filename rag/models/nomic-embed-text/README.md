# Nomic Embed Text ONNX assets

Place local ONNX embedding files here: https://huggingface.co/nomic-ai/nomic-embed-text-v1.5/tree/main

1. Download `tokenizer.json`:
   https://huggingface.co/nomic-ai/nomic-embed-text-v1.5/resolve/main/tokenizer.json
2. Save it as:
   `rag/models/nomic-embed-text/tokenizer.json`
3. Download `model.onnx`:
   https://huggingface.co/nomic-ai/nomic-embed-text-v1.5/resolve/main/onnx/model_quantized.onnx
4. Save it as:
   `rag/models/nomic-embed-text/model.onnx`

Expected directory structure:

```text
rag/models/nomic-embed-text/
  model.onnx
  tokenizer.json
```

`model.onnx` is ignored by git because it is too large for the repository.
