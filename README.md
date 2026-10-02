# Matching Engine

## Livro de ofertas

O livro usa um TreeMap com chave composta de preço e prioridade:

- Compras: maior preço primeiro.
- Vendas: menor preço primeiro.
- Ordens no mesmo preço: menor sequência de prioridade primeiro, respeitando a chegada.

Cada ordem recebe um UUID gerado internamente. A prioridade usa um contador de tipo long
compartilhado pelos dois lados, independente do identificador. Um índice por UUID
permite localizar ordens para consulta e remoção.

## Ordens

### Tipo Limit

Cria uma nova ordem no livro pelo preço e quantidade informados. A Limit não
consulta o preço do lado oposto e não executa fill, parcial ou total. A ordem é
inserida mesmo quando seu preço permitiria um trade imediato, sem alterar as
quantidades das ordens existentes.

Nesta implementação, a Limit tem a responsabilidade de colocar ofertas no livro.
A execução de trades fica reservada às ordens Market,mantendo simples e 
separa a inserção de ofertas do consumo.