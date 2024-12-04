# Согласованность в конечном счёте

## Проблема

Современные информационные системы являются распределёнными, то есть части этой системы запущены и работают
на разных серверах, а данные между частями системы передаются по сети. 

Такой способ взаимодействия занимает значительно больше времени, чем передача данных внутри локальной машины.
Вдобавок, передача данных по сети значительно менее надёжна: может произойти таймаут запроса, или разрыв соединения.

Пусть есть сценарий: покупатель оплачивает заказ из ресторана через сервис-агрегатор доставки еды.
В этом сценарии, кроме покупателя, принимают участие несколько информационных систем, которые расположены
в разных физических местах и даже, скорее всего, обслуживаются разными компаниями.

Системы совершают запросы друг к дружке примерно по такой схеме:

```plantuml
actor покупатель as U
participant агрегатор as A
participant "платёжный шлюз" as G
participant банк as B
participant ресторан as R

U -> A : оплатить заказ
A -> G : выполнить платёж
G -> B : выполнить платёж

B -> G : подтверждение
G -> A : подтверждение

A -> R : передача заказа
R -> A : готовность выполнить заказ 
```

Сбой может произойти на каждом этапе, при:

1. Доставке данных запроса до узла.
2. Обработке запроса узлом.
3. Доставке данных ответа на запрос.

В таком случае, части системы могут прийти в несогласованное состояние.

Пример 1. Банк подтвердил платёж, но произошёл разрыв сетевого соединения при доставке ответа.
Агрегатор может прийти к выводу, что платёж не был совершён, и отменить заказ. При этом, 
банк считает, что платёж совершён успешно, и списывает деньги со счёта.

Пример 2. Платёжный шлюз получил запрос от агрегатора, и во время обработки этого запроса возникла ошибка, 
вызванная недоступностью БД шлюза. Агрегатор получил от шлюза ответ с кодом состояния 500, но при этом неизвестно, 
передал ли шлюз запрос в банк, и были ли списаны деньги.

В общем случае, нельзя ожидать, что в каждый момент времени все части распределённой системы
будут доступны и работать. Поэтому, принимается как данность, что в определённый момент времени
части системы могут быть рассогласованы, а при обработке запроса может возникнуть сбой.

## Идемпотентность

Если одна сторона сделала попытку отправить запрос, но точно не ясно, был ли запрос доставлен и успешно обработан,
то вызывающая сторона должна повторять попытки до тех пор, пока не получит однозначные данные, например, что запрос
обработан успешно.

Чтобы такая схема корректно работала, принимающая сторона должна уметь корректно обрабатывать запросы на
повторное изменение одних и те же данных, например, повторные запросы на оплату заказа. Такое свойство
системы называется **идемпотентностью**. 

От вызывающей стороны требуется вместе с каждым запроса, который должен быть идемпотентным, передавать
специальное значение -- токен или ключ идемпотентности. Именно с помощью ключа идемпотентности принимающая
сторона отличает повторный запрос от нового запроса.

Рекомендуется прочитать статью [Стажёр Вася и его истории об идемпотентности API](https://habr.com/ru/companies/yandex/articles/442762/).
В ней описаны некоторые проблемы, которые могут возникать при разных сбоях, и как можно обрабатывать такие проблемы.

## Сага

При выполнении распределённой (изменяющей состояние нескольких систем) транзакции может возникнуть ситуация, при которой
некоторые части системы уже внесли в своё состояние изменение, но транзакция всё равно не может быть завершена.

Пример: платёж был успешно совершён, и заказ был передан в ресторан, но там случилась авария в производственных
помещениях. Работа ресторана остановлена, нет технической возможности исполнить заказ, и он должен быть отменён.
Однако платёжный шлюз и банк уже отметили платёж как выполненный. 

Для таких случаев, используется паттерн Сага. Составная транзакция, изменяющая состояния нескольких систем, 
разбивается на последовательность транзакций, каждая из которых затрагивает только свою систему.
Транзакции выполняются одна за другой, а если в одной из них произошёл необратимый сбой, то запускается
цепочка компенсирующих транзакций, которые должны отменить внесённые изменения.

В приведённом примере, компенсирующей транзакцией будет отмена платежа. 
При обработке этого запроса тоже может произойти сбой, поэтому и запрос на отмену
должен быть идемпотентным.

Подробнее о паттерне Сага можно прочитать в [статье](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga).