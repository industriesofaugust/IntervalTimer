package com.august.intervaltimer.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEditTest {

    private fun work(seconds: Int, name: String = "Work") = Interval(name, IntervalType.WORK, seconds)
    private fun rest(seconds: Int, name: String = "Rest") = Interval(name, IntervalType.REST, seconds)

    private fun session(rounds: Int, workSeconds: Int = 40, restSeconds: Int = 20) =
        buildSession(QuickBuildConfig(rounds, workSeconds, restSeconds))

    // workCount

    @Test
    fun `work count counts only work intervals`() {
        assertEquals(3, workCount(session(3)))
        assertEquals(0, workCount(listOf(rest(10))))
        assertEquals(0, workCount(emptyList()))
    }

    // lastDurationOf

    @Test
    fun `last duration is taken from the last interval of that type`() {
        val list = listOf(work(30), rest(10), work(45), rest(15))
        assertEquals(45, lastDurationOf(list, IntervalType.WORK))
        assertEquals(15, lastDurationOf(list, IntervalType.REST))
    }

    @Test
    fun `last duration falls back to the defaults`() {
        assertEquals(DEFAULT_WORK_SECONDS, lastDurationOf(emptyList(), IntervalType.WORK))
        assertEquals(DEFAULT_REST_SECONDS, lastDurationOf(listOf(work(30)), IntervalType.REST))
    }

    // commonDuration

    @Test
    fun `common duration is the shared value`() {
        val list = session(4, workSeconds = 30, restSeconds = 10)
        assertEquals(30, commonDuration(list, IntervalType.WORK))
        assertEquals(10, commonDuration(list, IntervalType.REST))
    }

    @Test
    fun `common duration is null when mixed`() {
        val list = listOf(work(30), rest(10), work(45))
        assertNull(commonDuration(list, IntervalType.WORK))
        assertEquals(10, commonDuration(list, IntervalType.REST))
    }

    @Test
    fun `common duration is null when there are none`() {
        assertNull(commonDuration(emptyList(), IntervalType.WORK))
        assertNull(commonDuration(session(1), IntervalType.REST))
    }

    // withAllDurations

    @Test
    fun `all durations of one type change and the other type is left alone`() {
        val list = listOf(work(30), rest(10), work(45), rest(15))
        val result = withAllDurations(list, IntervalType.WORK, 50)
        assertEquals(listOf(50, 10, 50, 15), result.map { it.durationSeconds })
    }

    @Test
    fun `all durations are kept within the allowed range`() {
        val list = listOf(work(30))
        assertEquals(MAX_INTERVAL_SECONDS, withAllDurations(list, IntervalType.WORK, 99999).single().durationSeconds)
        assertEquals(MIN_INTERVAL_SECONDS, withAllDurations(list, IntervalType.WORK, 0).single().durationSeconds)
    }

    @Test
    fun `all durations on an empty list is empty`() {
        assertEquals(emptyList<Interval>(), withAllDurations(emptyList(), IntervalType.REST, 20))
    }

    // withRounds

    @Test
    fun `fewer rounds drops the end of the list`() {
        val result = withRounds(session(5), 3)
        assertEquals(session(3).map { it.type }, result.map { it.type })
        // The names are renumbered separately, by withGeneratedNames.
        assertEquals(session(3), withGeneratedNames(result))
    }

    @Test
    fun `fewer rounds also drops trailing rests`() {
        val list = session(4) + rest(20)
        val result = withRounds(list, 4 - 1)
        assertEquals(IntervalType.WORK, result.last().type)
        assertEquals(3, workCount(result))
        assertEquals(5, result.size)
    }

    @Test
    fun `same number of rounds leaves the list as it is`() {
        val list = session(3) + rest(20)
        assertEquals(list, withRounds(list, 3))
    }

    @Test
    fun `more rounds adds a rest and a work interval for each`() {
        val result = withRounds(session(2, workSeconds = 30, restSeconds = 10), 4)
        assertEquals(
            listOf(
                IntervalType.WORK, IntervalType.REST, IntervalType.WORK, IntervalType.REST,
                IntervalType.WORK, IntervalType.REST, IntervalType.WORK,
            ),
            result.map { it.type },
        )
        assertEquals(listOf(30, 10, 30, 10, 30, 10, 30), result.map { it.durationSeconds })
    }

    @Test
    fun `more rounds copies the last durations not the first`() {
        val list = listOf(work(30), rest(10), work(45), rest(15), work(50))
        val added = withRounds(list, 4).drop(list.size)
        assertEquals(listOf(15, 50), added.map { it.durationSeconds })
    }

    @Test
    fun `more rounds after a trailing rest does not add a second rest`() {
        val list = session(2) + rest(20)
        val result = withRounds(list, 3)
        assertEquals(
            listOf(
                IntervalType.WORK, IntervalType.REST, IntervalType.WORK,
                IntervalType.REST, IntervalType.WORK,
            ),
            result.map { it.type },
        )
    }

    @Test
    fun `more rounds on an empty list adds just work`() {
        val result = withRounds(emptyList(), 3)
        assertEquals(
            listOf(IntervalType.WORK, IntervalType.REST, IntervalType.WORK, IntervalType.REST, IntervalType.WORK),
            result.map { it.type },
        )
        assertEquals(listOf(40, 20, 40, 20, 40), result.map { it.durationSeconds })
    }

    @Test
    fun `rounds are kept between one and the maximum`() {
        assertEquals(1, workCount(withRounds(session(5), 0)))
        assertEquals(1, workCount(withRounds(session(5), -3)))
        assertEquals(MAX_ROUNDS, workCount(withRounds(session(5), 500)))
    }

    @Test
    fun `a list of rests only grows without a leading rest`() {
        val result = withRounds(listOf(rest(20)), 2)
        assertEquals(
            listOf(IntervalType.REST, IntervalType.WORK, IntervalType.REST, IntervalType.WORK),
            result.map { it.type },
        )
    }

    // repeated

    @Test
    fun `repeating puts a rest between the two copies`() {
        val list = session(2, workSeconds = 30, restSeconds = 10)
        val result = repeated(list, restBetweenSeconds = 25)
        assertEquals(7, result.size)
        assertEquals(rest(25), result[3])
        assertEquals(list, result.take(3))
        assertEquals(list, result.drop(4))
    }

    @Test
    fun `repeating a list that ends in rest adds no extra rest`() {
        val list = session(2) + rest(20)
        val result = repeated(list, restBetweenSeconds = 99)
        assertEquals(list + list, result)
    }

    @Test
    fun `repeating an empty list gives an empty list`() {
        assertEquals(emptyList<Interval>(), repeated(emptyList(), 20))
    }

    // isGeneratedName

    @Test
    fun `generated names are blank, plain, or numbered`() {
        assertTrue(isGeneratedName(""))
        assertTrue(isGeneratedName("   "))
        assertTrue(isGeneratedName("Work"))
        assertTrue(isGeneratedName("Rest"))
        assertTrue(isGeneratedName("Work 3 of 8"))
        assertTrue(isGeneratedName("Work 12 of 50"))
    }

    @Test
    fun `typed names are not generated`() {
        assertFalse(isGeneratedName("Squats"))
        assertFalse(isGeneratedName("Work 3"))
        assertFalse(isGeneratedName("Work 3 of 8 reps"))
        assertFalse(isGeneratedName("Rest day"))
        assertFalse(isGeneratedName("work"))
    }

    // withGeneratedNames

    @Test
    fun `generated names are renumbered after rounds change`() {
        val grown = withGeneratedNames(withRounds(session(2), 4))
        assertEquals(
            listOf("Work 1 of 4", "Rest", "Work 2 of 4", "Rest", "Work 3 of 4", "Rest", "Work 4 of 4"),
            grown.map { it.name },
        )
        val shrunk = withGeneratedNames(withRounds(grown, 2))
        assertEquals(listOf("Work 1 of 2", "Rest", "Work 2 of 2"), shrunk.map { it.name })
    }

    @Test
    fun `typed names are kept and still count towards the numbering`() {
        val list = listOf(work(40, "Squats"), rest(20), work(40), rest(20), work(40, "Lunges"), rest(20), work(40))
        val result = withGeneratedNames(list)
        assertEquals(
            listOf("Squats", "Rest", "Work 2 of 4", "Rest", "Lunges", "Rest", "Work 4 of 4"),
            result.map { it.name },
        )
    }

    @Test
    fun `a rest named like work and a work named rest are fixed`() {
        val list = listOf(work(40, "Rest"), rest(20, "Work 1 of 2"), work(40, ""))
        assertEquals(listOf("Work 1 of 2", "Rest", "Work 2 of 2"), withGeneratedNames(list).map { it.name })
    }

    @Test
    fun `blank names are filled in`() {
        val list = listOf(work(40, ""), rest(20, " "))
        assertEquals(listOf("Work 1 of 1", "Rest"), withGeneratedNames(list).map { it.name })
    }

    @Test
    fun `a single round is named like quick build names it`() {
        assertEquals(session(1), withGeneratedNames(listOf(work(40, ""))))
        assertEquals(session(1), withGeneratedNames(listOf(work(40))))
    }

    @Test
    fun `generated names match quick build for a longer session`() {
        val messy = session(5).map { it.copy(name = "Work") }
        assertEquals(session(5), withGeneratedNames(messy))
    }

    @Test
    fun `naming an empty list is fine`() {
        assertEquals(emptyList<Interval>(), withGeneratedNames(emptyList()))
    }

    // suggestedTemplateName

    @Test
    fun `suggested name shows rounds and the shared durations`() {
        assertEquals("8× 0:40/0:20", suggestedTemplateName(session(8)))
        assertEquals("5× 1:00/1:30", suggestedTemplateName(session(5, 60, 90)))
    }

    @Test
    fun `suggested name is plain workout when durations differ`() {
        assertEquals("Workout", suggestedTemplateName(listOf(work(30), rest(10), work(45))))
        assertEquals("Workout", suggestedTemplateName(listOf(work(30), rest(10), work(30), rest(15), work(30))))
        assertEquals("Workout", suggestedTemplateName(emptyList()))
    }

    @Test
    fun `suggested name leaves out the rest when there is none`() {
        assertEquals("1× 0:40", suggestedTemplateName(session(1)))
    }

    // starters

    @Test
    fun `starters keep the names the watch used`() {
        assertEquals(
            listOf("Tabata 8×20/10", "HIIT 8×40/20", "Strength 5×60/90"),
            starterTemplates.map { it.name },
        )
        assertEquals(listOf("Tabata", "HIIT", "Strength"), starterTemplates.map { it.shortName })
    }

    @Test
    fun `starters build the same session as quick build`() {
        assertEquals(session(8, 20, 10), starterTemplates[0].intervals())
        assertEquals(session(8, 40, 20), starterTemplates[1].intervals())
        assertEquals(session(5, 60, 90), starterTemplates[2].intervals())
    }

    @Test
    fun `every starter name matches its numbers`() {
        starterTemplates.forEach { starter ->
            val numbers = "${starter.rounds}×${starter.workSeconds}/${starter.restSeconds}"
            assertEquals("${starter.shortName} $numbers", starter.name)
            assertEquals(starter.rounds, workCount(starter.intervals()))
        }
    }
}
