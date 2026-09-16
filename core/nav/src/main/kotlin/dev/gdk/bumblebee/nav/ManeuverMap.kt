package dev.gdk.bumblebee.nav

/** Mappls maneuver ID → cluster byte (a531 byte 2). V-Strom / Gixxer default branch. */
object ManeuverMap {
    const val DEFAULT_CLUSTER_BYTE = 8
    const val NO_MANEUVER_BYTE = 46

    fun mapplsIdToClusterByte(mapplsId: Int): Int? = when (mapplsId) {
        0 -> 1
        1 -> 2
        2 -> 3
        3 -> 4
        4 -> 5
        5 -> 6
        6 -> 7
        7 -> 8
        8, 9, 10 -> 9
        11 -> 11
        12 -> 12
        13 -> 13
        14 -> 14
        15 -> 31
        16 -> 32
        17 -> 29
        18 -> 30
        19 -> 27
        20 -> 28
        21 -> 33
        22 -> 34
        23 -> 35
        24 -> 36
        25 -> 37
        26, 27, 28 -> 31
        30, 31 -> 32
        41 -> 39
        50 -> 40
        51 -> 41
        52 -> 42
        53 -> 15
        54 -> 16
        55 -> 17
        56 -> 18
        57 -> 19
        58 -> 46
        59 -> 47
        60 -> 48
        61 -> 49
        62 -> 50
        63 -> 51
        64 -> 52
        65 -> 20
        66 -> 21
        67 -> 22
        68 -> 23
        69 -> 24
        70 -> 25
        71 -> 26
        72 -> 45
        73 -> 38
        74 -> 44
        75 -> 10
        else -> null
    }
}
