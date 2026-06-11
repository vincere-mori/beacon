package app.mayak.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HoverAnimatorTest {
    @Test
    fun modeAnimationProducesEnoughFrames() {
        val samples = mutableListOf<Float>()
        val finished = CountDownLatch(1)

        SwingUtilities.invokeAndWait {
            HoverAnimator(JPanel(), durationMs = 260) { value ->
                samples += value
                if (value >= 1f) finished.countDown()
            }.setTarget(1f)
        }

        assertTrue(finished.await(2, TimeUnit.SECONDS))
        assertTrue(samples.size >= 3)
        assertTrue(samples.dropLast(1).any { it in 0.01f..0.99f })
        assertEquals(1f, samples.last())
    }
}
