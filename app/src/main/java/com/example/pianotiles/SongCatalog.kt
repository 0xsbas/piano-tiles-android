package com.example.pianotiles

data class Song(val title: String, val composer: String, val baseSpeed: Float, val notes: IntArray)

object SongCatalog {
    val songs = listOf(
        Song(
            title = "Turkish March",
            composer = "W.A. Mozart",
            baseSpeed = 820f,
            notes = intArrayOf(
                71, 69, 68, 69, 72,
                74, 72, 71, 72, 76,
                77, 76, 75, 76, 83, 81, 80, 81, 83, 81, 80, 81, 84,
                81, 84, 79, 81, 83, 81, 79, 81, 83, 81, 79, 81, 83, 81, 79, 78, 76
            )
        ),
        Song(
            title = "Minuet in G Major",
            composer = "J.S. Bach",
            baseSpeed = 700f,
            notes = intArrayOf(
                74, 67, 69, 71, 72, 74, 67, 67,
                76, 72, 74, 76, 78, 79, 67, 67,
                72, 74, 72, 71, 69, 71, 72, 71, 69, 67,
                66, 67, 69, 71, 67, 69
            )
        ),
        Song(
            title = "Ode to Joy",
            composer = "L. van Beethoven",
            baseSpeed = 660f,
            notes = intArrayOf(
                64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 64, 62, 62,
                64, 64, 65, 67, 67, 65, 64, 62, 60, 60, 62, 64, 62, 60, 60
            )
        ),
        Song(
            title = "Für Elise",
            composer = "L. van Beethoven",
            baseSpeed = 740f,
            notes = intArrayOf(
                76, 75, 76, 75, 76, 71, 74, 72, 69,
                60, 64, 69, 71, 64, 68, 71, 72,
                64, 76, 75, 76, 75, 76, 71, 74, 72, 69,
                60, 64, 69, 71, 64, 72, 71, 69
            )
        )
    )
}
