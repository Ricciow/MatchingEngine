# Matching Engine

## Livro de ofertas

O livro usa um TreeMap com chave composta de preço e prioridade:

- Compras: maior preço primeiro.
- Vendas: menor preço primeiro.
- Ordens no mesmo preço: menor sequência de prioridade primeiro, respeitando a chegada.

Cada ordem recebe um UUID gerado internamente. A prioridade usa um contador de tipo long
compartilhado pelos dois lados, independente do identificador. Um índice por UUID
permite localizar ordens para consulta e remoção.