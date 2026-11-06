package com.baijum.purandara.domain.varisai.catalog

/**
 * Sarali Varisai 1–16, in Mayamalavagowla. Traditional exercises.
 *
 * Copied verbatim from the `varisai` blocks of the author's Mayamalavagowla
 * practice workbook so it can be compared line by line. Notation grammar:
 * docs/plan.md, section 3.2.
 */
internal val SARALI_VARISAI_NOTATION: String = """
1
S R S R | S R | G M || S R G M | P D | N S′
S′ N S′ N | S′ N | D P || S′ N D P | M G | R S

2
S R G S | R G | S R || S R G M | P D | N S′
S′ N D S′ | N D | S′ N || S′ N D P | M G | R S

3
S R G M | S R | G M || S R G M | P D | N S′
S′ N D P | S′ N | D P || S′ N D P | M G | R S

4
S R G M | P , | S R || S R G M | P D | N S′
S′ N D P | M , | S′ N || S′ N D P | M G | R S

5
S R G M | P D | S R || S R G M | P D | N S′
S′ N D P | M G | S′ N || S′ N D P | M G | R S

6
S R G M | P D | N , || S R G M | P D | N S′
S′ N D P | M G | R , || S′ N D P | M G | R S

7
S R G M | P M | G R || S R G M | P D | N S′
S′ N D P | M P | D N || S′ N D P | M G | R S

8
S R G M | P M | D P || S R G M | P D | N S′
S′ N D P | M P | G M || S′ N D P | M G | R S

9
S R G M | R G | M P || S R G M | P D | N S′
S′ N D P | N D | P M || S′ N D P | M G | R S

10
S R G M | S M | G R || S R G M | P D | N S′
S′ N D P | S′ P | D N || S′ N D P | M G | R S

11
S G R M | G P | M G || S R G M | P D | N S′
S′ D N P | D M | P D || S′ N D P | M G | R S

12
S R S G | R G | M G || S R G M | P D | N S′
S′ N S′ D | N D | P D || S′ N D P | M G | R S

13
R S G R | M G | P M || S R G M | P D | N S′
N S′ D N | P D | M P || S′ N D P | M G | R S

14
G R S M | G R | P M || S R G M | P D | N S′
D N S′ P | D N | M P || S′ N D P | M G | R S

15
M G R S | P M | G R || S R G M | P D | N S′
P D N S′ | M P | D N || S′ N D P | M G | R S

16
S R G M | S′ N | D P || S R G M | P D | N S′
P D N S′ | M G | R S || S′ N D P | M G | R S
""".trimIndent()
