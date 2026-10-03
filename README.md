# Matching Engine

## Como rodar

- JDK 21 ou superior, com `java` e `javac` disponíveis no `PATH`.
- Maven, com `mvn` disponível no `PATH`.

Na raiz do projeto, compile e execute os testes:

```sh
mvn clean package
```

Depois, inicie o console interativo:

```sh
java -jar target/MatchingEngine-1.0-SNAPSHOT.jar
```

Para executar somente os testes:

```sh
mvn test
```

### Comandos do console

Digite os comandos no prompt `>>>`. Use ponto como separador decimal nos preços.

| Comando | Descrição |
| --- | --- |
| `limit <buy\|sell> <price> <qty>` | Insere uma ordem Limit com preço e quantidade. |
| `market <buy\|sell> <qty>` | Executa uma ordem Market. |
| `peg <bid\|offer> <buy\|sell> <qty>` | Insere uma ordem Peg; `bid` referencia as compras e `offer`, as vendas. |
| `book` | Exibe o livro de ofertas. |
| `cancel order <id>` | Cancela uma ordem pelo UUID retornado na criação. |
| `update order <id> <price> <qty>` | Atualiza o preço e a quantidade de uma ordem. |
| `quit` | Encerra o programa. |

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

- `order`: a ordem que ficou no livro, inserida pela Limit ou Peg, ou atualizada pela Update.
É vazia para Market, Cancelamento.
- `trades`: a lista de execuções. É vazia para Limit, Peg, Cancelamento e Update.
Para Market, soma os fills consecutivos no mesmo preço em um trade.
Se o preço reaparecer após outro preço, gera um novo trade.

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

### Tipo Peg

Ordens Peg recebem um lado de referência. No próprio lado seguem a melhor Limit;
no lado oposto seguem a melhor ordem, incluindo Pegs. Em ciclos, a Peg mais velha define 
o preço vem da melhor Limit do seu lado de referência e é compartilhado pelo ciclo.
Sem referência, usam o preço de 1 centavo, pois orders com valor 0 são inválidas e esse teoricamente seria o menor valor possível.
As Pegs atualizam o seu preço assim que a referência muda, inclusive entre fills
de um Market. O próximo fill considera o novo preço e a prioridade original.
Um fill parcial altera apenas a quantidade restante, sem recalcular os preços.
O repricing preserva a prioridade de chegada. Ao mudar para um preço onde já
existem outras ordens, a Peg é posicionada conforme sua chegada original.

### Cancelamento

Remove uma ordem por seu id do livro.

### Atualização

Atualiza uma ordem por seu ‘id’, alterando o seu preço ou quantidade a comprar/vender, 
alterar a quantidade modifica o valor restante a ser comprado. Assim como nas pegs, 
uma atualização não altera sua prioridade chegada, apenas a prioridade de preço.
Para ordens com price policy, como o peg, o preço é ignorado e apenas a quantidade
é alterada, pois o preço do peg é definido pelas outras orders.
