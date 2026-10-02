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

- `order`: a ordem que ficou no livro, inserida pela Limit ou atualizada pela Update.
É vazia para Market, Cancelamento.
- `trades`: a lista de execuções. É vazia para Limit, Cancelamento e Update.
Para Market, contém um trade por preço, somando os fills nesse preço.

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
Consome até a quantidade solicitada ou até esgotar a liquidez do lado oposto.
Se forem solicitadas 150 unidades e houver apenas 100, executa as 100 disponíveis.
A quantidade não preenchida é descartada; a Market não fica no livro. Se o lado
oposto estiver vazio, retorna uma lista de trades vazia.

### Cancelamento

Remove uma ordem por seu id do livro.

### Atualização

Atualiza uma ordem por seu ‘id’, alterando o seu preço ou quantidade a comprar/vender, 
alterar a quantidade modifica o valor restante a ser comprado. Assim como nas pegs, 
uma atualização não altera sua prioridade chegada, apenas a prioridade de preço.
