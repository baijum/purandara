package com.baijum.purandara.domain.varisai.catalog

/**
 * Dhattu Varisai 1–6, in Mayamalavagowla. Traditional exercises.
 *
 * Copied verbatim from the `varisai` blocks of the author's Mayamalavagowla
 * practice workbook so it can be compared line by line. Notation grammar:
 * docs/plan.md, section 3.2.
 */
internal val DHATTU_VARISAI_NOTATION: String = """
1
S M G R | S R | G M
R P M G | R G | M P
G D P M | G M | P D
M N D P | M P | D N
P S′ N D | P D | N S′
S′ P D N | S′ N | D P
N M P D | N D | P M
D G M P | D P | M G
P R G M | P M | G R
M S R G | M G | R S

2
S G R G | S R | G M
R M G M | R G | M P
G P M P | G M | P D
M D P D | M P | D N
P N D N | P D | N S′
S′ D N D | S′ N | D P
N P D P | N D | P M
D M P M | D P | M G
P G M G | P M | G R
M R G R | M G | R S

3
S M G M | R G | S R || S M G R | S R | G M
R P M P | G M | R G || R P M G | R G | M P
G D P D | M P | G M || G D P M | G M | P D
M N D N | P D | M P || M N D P | M P | D N
P S′ N S′ | D N | P D || P S′ N D | P D | N S′
S′ P D P | N D | S′ N || S′ P D N | S′ N | D P
N M P M | D P | N D || N M P D | N D | P M
D G M G | P M | D P || D G M P | D P | M G
P R G R | M G | P M || P R G M | P M | G R
M S R S | G R | M G || M S R G | M G | R S

4
S R S G | R M | G R || S G R G | S R | G M
R G R M | G P | M G || R M G M | R G | M P
G M G P | M D | P M || G P M P | G M | P D
M P M D | P N | D P || M D P D | M P | D N
P D P N | D S′ | N D || P N D N | P D | N S′
S′ N S′ D | N P | D N || S′ D N D | S′ N | D P
N D N P | D M | P D || N P D P | N D | P M
D P D M | P G | M P || D M P M | D P | M G
P M P G | M R | G M || P G M G | P M | G R
M G M R | G S | R G || M R G R | M G | R S

5
S R S G | R G | R M || S M G R | S R | G M
R G R M | G P | G M || R P M G | R G | M P
G M G P | M D | M P || G D P M | G M | P D
M P M D | P N | P D || M N D P | M P | D N
P D P N | D S′ | D N || P S′ N D | P D | N S′
S′ N S′ D | N P | N D || S′ P D N | S′ N | D P
N D N P | D M | D P || N M P D | N D | P M
D P D M | P G | P M || D G M P | D P | M G
P M P G | M R | M G || P R G M | P M | G R
M G M R | G S | G R || M S R G | M G | R S

6
S M G M | R G | S R || S G R G | S R | G M
R P M P | G M | R G || R M G M | R G | M P
G D P D | M P | G M || G P M P | G M | P D
M N D N | P D | M P || M D P D | M P | D N
P S′ N S′ | D N | P D || P N D N | P D | N S′
S′ P D P | N D | S′ N || S′ D N D | S′ N | D P
N M P M | D P | N D || N P D P | N D | P M
D G M G | P M | D P || D M P M | D P | M G
P R G R | M G | P M || P G M G | P M | G R
M S R S | G R | M G || M R G R | M G | R S
""".trimIndent()
