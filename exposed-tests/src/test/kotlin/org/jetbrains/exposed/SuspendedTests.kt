package org.jetbrains.exposed

import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.junit.Test
import kotlin.test.assertEquals

class SuspendedTests {

  val db: Database

  init {
    System.setProperty("com.zaxxer.hikari.housekeeping.periodMs", "1000")
    db = PsqlTestContainer.db
  }


  private suspend fun queryJob(i: Int): Int {
    val query = """
              SELECT pg_sleep(2), $i
            """.trimIndent()
    withContext(Dispatchers.IO) {
      newSuspendedTransaction(db = db) {
        exec(query) {
          println("Mapper started for job $i")
        }
      }
    }

    return i
  }

  data class Result(val res1: Int?, val res2: Int?, val res3: Int?)

  private suspend fun runJobs(shouldCancel: Boolean): Result {
    return coroutineScope {
      val job1 = async(CoroutineName("job-1")) { queryJob(1) }
      val job2 = async(CoroutineName("job-2")) { queryJob(2) }
      delay(100)
      if (shouldCancel) {
        println("Cancelling job1")
        job1.cancel()
        println("Cancelled job1, joining job1")
        job1.join()
        println("job1 is completed: $job1")
      }
      val job3 = withTimeoutOrNull(10_000) { async(CoroutineName("job-3")) { queryJob(3) } }
      Result(job1.awaitOrNull(), job2.awaitOrNull(), job3?.awaitOrNull())
    }
  }

  private suspend fun <T> Deferred<T>.awaitOrNull(): T? =
    try {
      await()
    } catch (e: Exception) {
      null
    }

  @Test
  fun withoutCancellation() {
    runBlocking {
      val result = runJobs(false)
      assertEquals(1, result.res1)
      assertEquals(2, result.res2)
      assertEquals(3, result.res3)
    }
  }

  @Test
  fun withCancellation() {
    runBlocking {
      repeat(10) {
        val result = runJobs(true)
        assert(result.res1 == 1 || result.res1 == null) { "Expecting res1 to be 1 or null " }
        assertEquals(2, result.res2)
        assertEquals(3, result.res3)
      }
      repeat(3) { println() }
      println("-----------------")
      repeat(3) { println() }
    }
  }

  /**
  private suspend fun <T : Any> execRawSql(dispatcher: CoroutineDispatcher, query: String, context: CoroutineContext? = null, db: Database? = null, block: (ResultSet) -> T): T? {
  val newContext = coroutineContext + dispatcher + (context ?: EmptyCoroutineContext)

  return withContext(newContext) {
  newSuspendedTransaction(db = db) {
  exec(query, block)
  }
  }
  }

  private suspend fun queryJobOld(i: Int): Int? {
  val query = """
  SELECT pg_sleep(2), $i
  """.trimIndent()
  val context: CoroutineContext? = null
  return execRawSql(Dispatchers.IO, query, context, db) { i }
  }

  fun cancellation() {

  runBlocking {
  coroutineScope {

  }


  coroutineScope {
  val job1 = launch {
  queryJob(1)
  println("job1 is complete")
  }
  //          delay(100)
  val job2 = launch {
  queryJob(2)
  println("job2 is complete")
  }
  delay(100)
  println("Cancelling job 1")
  job1.cancelAndJoin()
  println("Cancelled job 1")
  }
  println("Scope complete")
  delay(10_000)
  launch {
  queryJob(3)
  println("job3 is complete")
  }
  }
  }
   */
}
