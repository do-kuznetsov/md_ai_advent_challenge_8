# BGE Reranker v2 M3 ONNX assets

Place local ONNX reranker files here:

1. Download `tokenizer.json`:
   https://huggingface.co/onnx-community/bge-reranker-v2-m3-ONNX/resolve/main/tokenizer.json
2. Save it as:
   `rag/models/bge-reranker-v2-m3/tokenizer.json`
3. Download `model_quantized.onnx`:
   https://huggingface.co/onnx-community/bge-reranker-v2-m3-ONNX/resolve/main/onnx/model_quantized.onnx
4. Rename `model_quantized.onnx` to `model.onnx`.
5. Save it as:
   `rag/models/bge-reranker-v2-m3/model.onnx`

Expected directory structure:

```text
rag/models/bge-reranker-v2-m3/
  model.onnx
  tokenizer.json
```

`model.onnx` is ignored by git because it is too large for the repository.
