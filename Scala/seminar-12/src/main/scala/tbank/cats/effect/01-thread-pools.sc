/*
В прошлой лекции мы рассмотрели Future и его альтернативу в cats-effect - файберы, которые
работают, как легкие потоки и выполняют различные вычисления на пуле потоков (Thread pool),
давайте подробнее рассмотрим какие есть пулы, как они работают и зачем они нужны
*/

/*
`cats-effect` рантайм состоит из 3 пуллов потоков:

- `ScheduledExecutorService` - 1 высокоприоритетный поток, для откладываемых во времени
операций (операций с таймерами)
- Блокирующий пул потоков для блокирующих операций - неограниченный кэширующий пул
- `CPU` пул для остальных операций

Для наглядности можно представлять такую картинку: concurrency-thread-pools.png
*/

/*
### CPU пул

`CPU` пул имеет свою кастомную реализацию, которая знает про существование файберов

Он реализует алгоритм кражи работы, который позаимствовали из фреймворка `tokio` из
языка программирования Rust

Посмотреть картинку work-stealing.png

Суть реализации - каждый поток имеет свою локальную очередь задач (работы)

При этом есть глобальная очередь задач, из которой периодически потоки также берут задачи

Каждый поток работает над своими локальными задачами, то есть если мы создаем
дочерние файберы, то они вероятнее всего окажутся в локальной очереди.

Это позволяет сохранять кэш процессора и не вымывать его при переключении файберов.

Реализация каждого файбера представляет собой рекурсию, где мы разбираем наше IO
вычисление и выполняем каждый заложенный внутри шаг.

Если мы достигаем семантической блокировки или асинхронного вызова,
то мы останавливаем исполнение и передаем управление другому файберу. Таким образом,
мы не тратим время на ожидание и занимаемся другой работой

Если мы достигаем определенного лимита по итерациям, то мы также останавливаем исполнение и передаем
управление другому файберу. Это необходимо для достижения честного исполнения файберов

Итого, запросы к нашему планировщику выглядят примерно так: scala-scheduler.png
*/

/*
### Блокирующие операции

Мы можем размечать синхронные блокирующие операции, чтобы они исполнялись на блокирующем
пуле потоков:
 */

trait IO1[+A]

object IO1 {
  def blocking[A](suspended: => A): IO1[A]          = ???
  def interruptible[A](suspended: => A): IO1[A]     = ???
  def interruptibleMany[A](suspended: => A): IO1[A] = ???
}

/*
Все вычисления помеченные, как `IO.blocking`, будут неотменяемыми и будут исполняться строго
на блокирующем пуле:
 */

import cats.effect.IO

def callToDb(): Unit = Thread.sleep(10000)

def program: IO[Unit] =
  for {
    _ <- IO.println("Executing query")
    _ <- IO.blocking(callToDb())
    _ <- IO.println("Executed query")
  } yield ()

/*
- Вывод "Executing query" и "Executed query" будут выполнены на основном потоке
- Блокирующей вызов к БД будет выполнен на блокирующем пуле

`IO.interruptible` и `IO.interruptibleMany` уже будут отменяемыми, путем вызова
`Thread.interrupt` один или более раз

Если нам по каким-то причинам нужно исполнить наше вычисление на другом пуле потоков,
то для этого есть оператор `IO.evalOn`:
 */

import scala.concurrent.ExecutionContext

trait IO2[+A] {
  def evalOn(ec: ExecutionContext): IO2[A]
}

/*
При этом достигается гарантия, что следующее вычисление будет исполнено на оригинальном
(заданном) пуле потоков
 */

// Картинки work stealing/blocking pool
