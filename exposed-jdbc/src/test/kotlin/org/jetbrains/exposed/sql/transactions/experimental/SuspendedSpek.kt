package org.jetbrains.exposed.sql.transactions.experimental

import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.Database
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.sql.ResultSet
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

object SuspendedSpek : Spek({
  this.defaultTimeout = 60_000
  val db = PsqlTestContainer.db

  suspend fun queryJob(db: Database, i: Int) {
    val query = "SELECT pg_sleep(2), $i".trimIndent()
    val context: CoroutineContext? = null
    execRawSql(Dispatchers.IO, query, context, db) { 0 }
  }

  describe("cancellation") {
    beforeEachTest {
      runBlocking {
        coroutineScope {
          val job1 = launch {
            queryJob(db, 1)
            println("job1 is complete")
          }

          delay(100)
          val job2 = launch {
            queryJob(db, 2)
            println("job2 is complete")
          }

          delay(100)

          println("Cancelling job 1")
          job1.cancelAndJoin()
          println("Cancelled job 1")
        }
      }
      println("Block complete")
    }

    it("returns the connection to the pool") {
      runBlocking {
        delay(10_000)
      }
    }
  }
})

internal suspend fun <T : Any> execRawSql(dispatcher: CoroutineDispatcher, query: String, context: CoroutineContext? = null, db: Database? = null, block: (ResultSet) -> T): Unit? {
  val newContext = coroutineContext + dispatcher + (context ?: EmptyCoroutineContext)

  return withContext(newContext) {
    continueSuspendedTransaction(db = db) {
      exec(query)
    }
  }
}
