# Matching Engine

## Livro de ofertas

O livro usa um TreeMap com chave composta de preço e prioridade:

- Compras: maior preço primeiro.
- Vendas: menor preço primeiro.
- Ordens no mesmo preço: menor sequência de prioridade primeiro, respeitando a chegada.

Cada ordem recebe um UUID gerado internamente. A prioridade usa um contador de tipo long
compartilhado pelos dois lados, independente do identificador. Um índice por UUID
permite localizar ordens para consulta, atualização e remoção.

## Ordens

Toda order retorna um resultado com dois campos:

- `order`: a ordem que ficou no livro. É vazia para orders do tipo Market.
- `trades`: a lista de execuções. É vazia para Limit e contém um trade por fill
  para Market, incluindo fills parciais.

### Tipo Limit

Cria uma nova ordem no livro pelo preço e quantidade informados. A Limit não
consulta o preço do lado oposto e não executa fill, parcial ou total. A ordem é
inserida mesmo quando seu preço permitiria um trade imediato, sem alterar as
quantidades das ordens existentes.

Nesta implementação, a Limit tem a responsabilidade de colocar ofertas no livro.
A execução de trades fica reservada às ordens Market, separando a inserção de
ofertas do consumo de liquidez.

### Tipo Market

Executa imediatamente contra as ordens do lado oposto, pelo melhor preço
disponível: uma compra consome as vendas de menor preço e uma venda consome as
compras de maior preço. No mesmo preço, a prioridade é a ordem de chegada.
Caso não haja liquidez suficiente no livro irá retornar um erro, isto para
manter o comportamento separado de inserção e remoção e ao mesmo tempo não
gerar orders que não são totalmente preenchidas, como tentar comprar 150 quando há
100 e somente receber 100.